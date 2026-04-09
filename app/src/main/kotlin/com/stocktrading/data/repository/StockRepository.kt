package com.stocktrading.data.repository

import android.util.Log
import com.stocktrading.data.api.KISApiClient
import com.stocktrading.data.database.PortfolioDao
import com.stocktrading.data.database.PriceDataDao
import com.stocktrading.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 주식 데이터 Repository
 * API와 로컬 DB를 조율하는 단일 진입점 (Single Source of Truth)
 */
@Singleton
class StockRepository @Inject constructor(
    private val kisApiClient: KISApiClient,
    private val priceDataDao: PriceDataDao,
    private val portfolioDao: PortfolioDao,
    private val credentialManager: com.stocktrading.security.SecureCredentialManager
) {
    companion object {
        private const val TAG = "StockRepository"
        /** 주가 데이터 보관 기간 (60 거래일 = 약 3개월) */
        private const val PRICE_DATA_RETENTION_DAYS = 60
    }

    // ============================
    // 주가 데이터
    // ============================

    /**
     * 특정 종목의 최근 주가 데이터 가져오기
     * DB에 충분한 데이터가 없으면 API에서 조회하여 저장
     */
    suspend fun getPriceData(stockCode: String, days: Int = 60): Result<List<PriceData>> {
        return try {
            val localData = priceDataDao.getRecentPriceData(stockCode, days)

            if (localData.size >= days) {
                Log.d(TAG, "[$stockCode] 로컬 DB에서 ${localData.size}개 주가 데이터 반환")
                Result.success(localData)
            } else {
                Log.i(TAG, "[$stockCode] 로컬 데이터 부족 (${localData.size}개), API에서 조회")
                fetchAndSavePriceData(stockCode, days)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$stockCode] 주가 데이터 조회 오류", e)
            Result.failure(e)
        }
    }

    /**
     * KIS API에서 주가 데이터를 가져와 DB에 저장
     */
    suspend fun fetchAndSavePriceData(stockCode: String, days: Int = 60): Result<List<PriceData>> {
        return try {
            val service = kisApiClient.getService()
            val isMockMode = false // SecureCredentialManager에서 가져와야 하지만 여기선 간소화

            // 모의/실전 모두 동일한 tr_id 사용 (시세 조회는 공통)
            val response = service.getDailyPrice(
                authorization = "",  // KISAuthInterceptor가 자동 주입
                appkey = "",         // KISAuthInterceptor가 자동 주입
                appsecret = "",      // KISAuthInterceptor가 자동 주입
                trId = "FHKST01010400",
                marketDivCode = "J",
                stockCode = stockCode,
                periodDivCode = "D",
                adjustPrice = "0"
            )

            if (response.isSuccessful && response.body()?.rt_cd == "0") {
                val outputs = response.body()!!.output2
                val priceDataList = outputs.mapNotNull { output ->
                    try {
                        PriceData(
                            stockCode = stockCode,
                            stockName = stockCode, // 종목명은 별도 조회 필요
                            date = output.stck_bsop_date,
                            openPrice = output.stck_oprc.toDoubleOrNull() ?: 0.0,
                            highPrice = output.stck_hgpr.toDoubleOrNull() ?: 0.0,
                            lowPrice = output.stck_lwpr.toDoubleOrNull() ?: 0.0,
                            closePrice = output.stck_clpr.toDoubleOrNull() ?: 0.0,
                            volume = output.acml_vol.toLongOrNull() ?: 0L,
                            priceChange = output.prdy_vrss.toDoubleOrNull() ?: 0.0,
                            changeRate = output.prdy_ctrt.toDoubleOrNull() ?: 0.0
                        )
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "[$stockCode] 주가 데이터 파싱 오류: $output")
                        null
                    }
                }

                if (priceDataList.isNotEmpty()) {
                    priceDataDao.insertAll(priceDataList)
                    Log.i(TAG, "[$stockCode] ${priceDataList.size}개 주가 데이터 저장 완료")
                }

                Result.success(priceDataList.take(days))
            } else {
                val errorMsg = "API 오류: ${response.code()} - ${response.body()?.msg1}"
                Log.e(TAG, "[$stockCode] $errorMsg")
                Result.failure(IllegalStateException(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$stockCode] API 통신 오류", e)
            Result.failure(e)
        }
    }

    /**
     * 현재가 조회
     */
    suspend fun getCurrentPrice(stockCode: String): Result<CurrentPrice> {
        return try {
            val service = kisApiClient.getService()
            // 모의/실전 모두 동일한 tr_id 사용 (현재가 조회는 공통)
            val response = service.getCurrentPrice(
                authorization = "",
                appkey = "",
                appsecret = "",
                trId = "FHKST01010100",
                marketDivCode = "J",
                stockCode = stockCode
            )

            if (response.isSuccessful && response.body()?.rt_cd == "0") {
                val output = response.body()!!.output!!
                Result.success(
                    CurrentPrice(
                        stockCode = stockCode,
                        stockName = output.hts_kor_isnm,
                        currentPrice = output.stck_prpr.toDoubleOrNull() ?: 0.0,
                        changeAmount = output.prdy_vrss.toDoubleOrNull() ?: 0.0,
                        changeRate = output.prdy_ctrt.toDoubleOrNull() ?: 0.0,
                        volume = output.acml_vol.toLongOrNull() ?: 0L
                    )
                )
            } else {
                Result.failure(IllegalStateException("현재가 조회 실패: ${response.body()?.msg1}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$stockCode] 현재가 조회 오류", e)
            Result.failure(e)
        }
    }

    // ============================
    // 포트폴리오
    // ============================

    /** 보유 종목 실시간 관찰 */
    fun observePortfolio(): Flow<List<Portfolio>> = portfolioDao.observeAllPortfolios()

    /** 보유 종목 목록 조회 */
    suspend fun getPortfolio(): List<Portfolio> = portfolioDao.getAllPortfolios()

    /** 특정 종목 보유 정보 */
    suspend fun getPortfolioItem(stockCode: String): Portfolio? =
        portfolioDao.getPortfolioByCode(stockCode)

    /**
     * KIS API에서 잔고 조회 후 DB 동기화
     */
    suspend fun syncPortfolioFromApi(
        accountNo: String,
        accountProductCode: String,
        isMockMode: Boolean
    ): Result<List<Portfolio>> {
        return try {
            val trId = if (isMockMode) "VTTC8434R" else "TTTC8434R"
            val service = kisApiClient.getService()

            val response = service.getBalance(
                authorization = "",
                appkey = "",
                appsecret = "",
                trId = trId,
                accountNo = accountNo,
                accountProductCode = accountProductCode
            )

            if (response.isSuccessful) {
                val body = response.body()
                // rt_cd "0" 또는 "00000" 모두 성공으로 처리 (KIS 응답 버전 차이)
                val isSuccess = body?.rt_cd == "0" || body?.rt_cd == "00000"

                if (!isSuccess) {
                    val errMsg = "잔고 조회 실패 [rt_cd=${body?.rt_cd}] ${body?.msg1}"
                    Log.e(TAG, errMsg)
                    return Result.failure(IllegalStateException(errMsg))
                }

                // output1이 null이거나 빈 배열인 경우 (보유 종목 없음)
                val output1 = body?.output1.orEmpty()
                if (output1.isEmpty()) {
                    Log.i(TAG, "보유 종목 없음 — 포트폴리오 비우기")
                    portfolioDao.deleteAll()
                    return Result.success(emptyList())
                }

                val portfolios = output1.mapNotNull { item ->
                    // 종목코드 없으면 건너뜀
                    val stockCode = item.pdno?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val quantity = item.hldg_qty?.toIntOrNull() ?: 0
                    if (quantity <= 0) return@mapNotNull null  // 수량 0이하 건너뜀

                    val avgBuyPrice = item.pchs_avg_pric?.toDoubleOrNull() ?: 0.0
                    val currentPrice = item.prpr?.toDoubleOrNull() ?: avgBuyPrice

                    Portfolio.create(
                        stockCode = stockCode,
                        stockName = item.prdt_name?.takeIf { it.isNotBlank() } ?: stockCode,
                        quantity = quantity,
                        buyPrice = avgBuyPrice,
                        currentPrice = currentPrice
                    )
                }

                // 기존 데이터 전체 교체
                portfolioDao.deleteAll()
                portfolioDao.insertOrUpdateAll(portfolios)

                Log.i(TAG, "포트폴리오 동기화 완료: ${portfolios.size}개 종목")
                Result.success(portfolios)
            } else {
                val errCode = response.code()
                val errBody = response.errorBody()?.string()
                val errMsg = "잔고 조회 HTTP 오류 [$errCode]: $errBody"
                Log.e(TAG, errMsg)
                Result.failure(IllegalStateException(errMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "포트폴리오 동기화 오류", e)
            Result.failure(e)
        }
    }

    /**
     * 보유 종목 현재가 업데이트
     */
    suspend fun updatePortfolioCurrentPrice(stockCode: String, currentPrice: Double) {
        val portfolio = portfolioDao.getPortfolioByCode(stockCode) ?: return
        val evaluationAmount = currentPrice * portfolio.quantity
        val profitLoss = evaluationAmount - portfolio.totalBuyAmount
        val profitRate = if (portfolio.totalBuyAmount > 0) (profitLoss / portfolio.totalBuyAmount) * 100 else 0.0

        portfolioDao.updateCurrentPrice(
            stockCode = stockCode,
            currentPrice = currentPrice,
            evaluationAmount = evaluationAmount,
            profitLoss = profitLoss,
            profitRate = profitRate
        )
    }

    /**
     * 오래된 주가 데이터 정리
     */
    suspend fun cleanupOldPriceData() {
        val cutoffDate = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -PRICE_DATA_RETENTION_DAYS)
        }
        val cutoffDateStr = String.format(
            "%04d%02d%02d",
            cutoffDate.get(java.util.Calendar.YEAR),
            cutoffDate.get(java.util.Calendar.MONTH) + 1,
            cutoffDate.get(java.util.Calendar.DAY_OF_MONTH)
        )

        val stockCodes = priceDataDao.getAllStockCodes()
        stockCodes.forEach { code ->
            val deleted = priceDataDao.deleteOldPriceData(code, cutoffDateStr)
            if (deleted > 0) {
                Log.d(TAG, "[$code] ${deleted}개 오래된 주가 데이터 삭제")
            }
        }
    }
}
