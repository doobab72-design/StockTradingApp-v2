package com.stocktrading.data.repository

import android.util.Log
import com.stocktrading.data.api.KISApiClient
import com.stocktrading.data.api.HashkeyResponse
import com.stocktrading.data.database.TradingHistoryDao
import com.stocktrading.data.model.*
import com.stocktrading.security.SecureCredentialManager
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 거래 실행 Repository
 * 주문 생성, 실행, 취소 및 거래 내역 관리
 */
@Singleton
class TradingRepository @Inject constructor(
    private val kisApiClient: KISApiClient,
    private val tradingHistoryDao: TradingHistoryDao,
    private val credentialManager: SecureCredentialManager
) {
    companion object {
        private const val TAG = "TradingRepository"
        /** 일일 최대 거래 횟수 */
        const val MAX_DAILY_TRADES = 10
        /** 1종목 최대 비중 (총 자산 대비) */
        const val MAX_STOCK_WEIGHT = 0.05
    }

    // ============================
    // 거래 내역 조회
    // ============================

    fun observeAllRecords(): Flow<List<TradingRecord>> = tradingHistoryDao.observeAllRecords()

    fun observePendingOrders(): Flow<List<TradingRecord>> = tradingHistoryDao.observePendingOrders()

    suspend fun getRecentRecords(limit: Int = 50): List<TradingRecord> =
        tradingHistoryDao.getRecentRecords(limit)

    suspend fun getPendingOrders(): List<TradingRecord> = tradingHistoryDao.getPendingOrders()

    // ============================
    // 위험 관리 체크
    // ============================

    /**
     * 오늘 이미 최대 거래 횟수를 초과했는지 확인
     */
    suspend fun isDailyLimitReached(): Boolean {
        val dayStart = getTodayMidnightMillis()
        val todayCount = tradingHistoryDao.getTodayTradeCount(dayStart)
        return todayCount >= MAX_DAILY_TRADES
    }

    /**
     * 1종목 최대 비중 5% 초과 여부 확인
     * @param totalAssets 총 자산
     * @param buyAmount 매수 예정 금액
     */
    fun isWeightLimitExceeded(totalAssets: Double, buyAmount: Double): Boolean {
        if (totalAssets <= 0) return false
        return (buyAmount / totalAssets) > MAX_STOCK_WEIGHT
    }

    // ============================
    // 주문 생성 (사용자 확인 대기)
    // ============================

    /**
     * 매수 주문 생성 (PENDING 상태로 저장, 사용자 확인 대기)
     */
    suspend fun createBuyOrder(
        stockCode: String,
        stockName: String,
        quantity: Int,
        price: Double,
        signalReason: String
    ): Result<TradingRecord> {
        // 일일 거래 한도 체크
        if (isDailyLimitReached()) {
            return Result.failure(IllegalStateException("일일 최대 거래 횟수(${MAX_DAILY_TRADES}회)를 초과했습니다."))
        }

        val record = TradingRecord(
            stockCode = stockCode,
            stockName = stockName,
            tradeType = TradeType.BUY,
            quantity = quantity,
            price = price,
            totalAmount = price * quantity,
            status = TradeStatus.PENDING,
            signalReason = signalReason
        )

        val id = tradingHistoryDao.insert(record)
        Log.i(TAG, "[$stockCode] 매수 주문 생성 (PENDING): ${quantity}주 @ ${price}원")
        return Result.success(record.copy(id = id))
    }

    /**
     * 매도 주문 생성 (PENDING 상태로 저장)
     */
    suspend fun createSellOrder(
        stockCode: String,
        stockName: String,
        quantity: Int,
        price: Double,
        signalReason: String
    ): Result<TradingRecord> {
        val record = TradingRecord(
            stockCode = stockCode,
            stockName = stockName,
            tradeType = TradeType.SELL,
            quantity = quantity,
            price = price,
            totalAmount = price * quantity,
            status = TradeStatus.PENDING,
            signalReason = signalReason
        )

        val id = tradingHistoryDao.insert(record)
        Log.i(TAG, "[$stockCode] 매도 주문 생성 (PENDING): ${quantity}주 @ ${price}원")
        return Result.success(record.copy(id = id))
    }

    // ============================
    // 주문 실행 (사용자 확인 후)
    // ============================

    /**
     * 사용자가 확인한 PENDING 주문을 KIS API로 실제 전송
     */
    suspend fun executeOrder(recordId: Long): Result<OrderResponse> {
        val record = tradingHistoryDao.getRecordById(recordId)
            ?: return Result.failure(IllegalArgumentException("거래 내역을 찾을 수 없습니다: id=$recordId"))

        if (record.status != TradeStatus.PENDING) {
            return Result.failure(IllegalStateException("이미 처리된 주문입니다: ${record.status}"))
        }

        val accountNo = credentialManager.getAccountNo()
            ?: return Result.failure(IllegalStateException("계좌번호가 설정되지 않았습니다."))
        val accountProductCode = credentialManager.getAccountProductCode() ?: "01"
        val isMockMode = credentialManager.isMockMode()

        return try {
            val service = kisApiClient.getService()

            val orderRequest = OrderRequest(
                CANO = accountNo,
                ACNT_PRDT_CD = accountProductCode,
                PDNO = record.stockCode,
                ORD_DVSN = "01", // 시장가 주문
                ORD_QTY = record.quantity.toString(),
                ORD_UNPR = "0"   // 시장가는 0
            )

            // Hashkey 발급
            val hashkeyResponse = getHashkey(orderRequest)

            val trId = when {
                isMockMode && record.tradeType == TradeType.BUY -> "VTTC0802U"
                isMockMode && record.tradeType == TradeType.SELL -> "VTTC0801U"
                !isMockMode && record.tradeType == TradeType.BUY -> "TTTC0802U"
                else -> "TTTC0801U"
            }

            val response = if (record.tradeType == TradeType.BUY) {
                service.buyStock(
                    authorization = "",
                    appkey = "",
                    appsecret = "",
                    trId = trId,
                    hashkey = hashkeyResponse,
                    request = orderRequest
                )
            } else {
                service.sellStock(
                    authorization = "",
                    appkey = "",
                    appsecret = "",
                    trId = trId,
                    hashkey = hashkeyResponse,
                    request = orderRequest
                )
            }

            val body = response.body()
            // rt_cd "0" 또는 "00000" 모두 성공 처리
            val isSuccess = response.isSuccessful &&
                    (body?.rt_cd == "0" || body?.rt_cd == "00000")

            if (isSuccess) {
                val orderId = body?.output?.ORNO
                tradingHistoryDao.updateOrderStatus(
                    id = recordId,
                    status = TradeStatus.ORDERED,
                    orderId = orderId
                )
                Log.i(TAG, "[${record.stockCode}] 주문 전송 성공: 주문번호=$orderId")
                Result.success(body!!)
            } else {
                val errCode = response.code()
                val msg = body?.msg1?.takeIf { it.isNotBlank() }
                    ?: response.errorBody()?.string()
                    ?: response.message()
                tradingHistoryDao.updateOrderStatus(id = recordId, status = TradeStatus.FAILED)
                Log.e(TAG, "[${record.stockCode}] 주문 전송 실패 [HTTP $errCode]: $msg")
                Result.failure(IllegalStateException("주문 실패: $msg"))
            }
        } catch (e: Exception) {
            tradingHistoryDao.updateOrderStatus(id = recordId, status = TradeStatus.FAILED)
            Log.e(TAG, "[${record.stockCode}] 주문 실행 오류", e)
            Result.failure(e)
        }
    }

    /**
     * PENDING 주문 취소
     */
    suspend fun cancelOrder(recordId: Long) {
        tradingHistoryDao.updateOrderStatus(id = recordId, status = TradeStatus.CANCELLED)
        Log.i(TAG, "주문 취소: id=$recordId")
    }

    // ============================
    // 유틸리티
    // ============================

    private suspend fun getHashkey(orderRequest: OrderRequest): String {
        return try {
            val appKey = credentialManager.getAppKey() ?: return ""
            val appSecret = credentialManager.getAppSecret() ?: return ""
            val service = kisApiClient.getService()

            val body = mapOf(
                "CANO" to orderRequest.CANO,
                "ACNT_PRDT_CD" to orderRequest.ACNT_PRDT_CD,
                "PDNO" to orderRequest.PDNO,
                "ORD_DVSN" to orderRequest.ORD_DVSN,
                "ORD_QTY" to orderRequest.ORD_QTY,
                "ORD_UNPR" to orderRequest.ORD_UNPR
            )

            val response = service.getHashkey(appKey, appSecret, body)
            response.body()?.HASH ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Hashkey 발급 오류", e)
            ""
        }
    }

    private fun getTodayMidnightMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
