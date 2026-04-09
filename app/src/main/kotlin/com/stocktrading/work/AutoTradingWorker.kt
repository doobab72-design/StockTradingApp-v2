package com.stocktrading.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.stocktrading.analysis.TradingSignalGenerator
import com.stocktrading.data.model.SignalType
import com.stocktrading.data.repository.StockRepository
import com.stocktrading.data.repository.TradingRepository
import com.stocktrading.notification.TradingNotificationManager
import com.stocktrading.security.SecureCredentialManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

/**
 * 2시간마다 실행되는 보유 종목 자동 분석 Worker
 * - 시장 시간(09:00 ~ 15:30)에만 실제 분석 수행
 * - 매매 신호 생성 시 사용자에게 푸시 알림 (사용자 확인 후 주문)
 */
@HiltWorker
class AutoTradingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val stockRepository: StockRepository,
    private val tradingRepository: TradingRepository,
    private val signalGenerator: TradingSignalGenerator,
    private val notificationManager: TradingNotificationManager,
    private val credentialManager: SecureCredentialManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AutoTradingWorker"
        const val WORK_NAME = "auto_trading"

        // 시장 시간 (KST)
        private const val MARKET_OPEN_HOUR = 9
        private const val MARKET_OPEN_MINUTE = 0
        private const val MARKET_CLOSE_HOUR = 15
        private const val MARKET_CLOSE_MINUTE = 30
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "보유 종목 분석 Worker 시작")

        // 시장 시간 확인
        if (!isMarketOpen()) {
            Log.d(TAG, "시장 시간 외 - 분석 건너뜀")
            return Result.success()
        }

        // API 키 설정 확인
        if (!credentialManager.isApiKeyConfigured()) {
            Log.w(TAG, "API 키 미설정 - 분석 건너뜀")
            return Result.success()
        }

        // 일일 거래 한도 확인
        if (tradingRepository.isDailyLimitReached()) {
            Log.w(TAG, "일일 최대 거래 횟수 도달 - 분석 건너뜀")
            return Result.success()
        }

        return try {
            // 포트폴리오 KIS API 동기화
            val accountNo = credentialManager.getAccountNo()
            val accountProductCode = credentialManager.getAccountProductCode() ?: "01"
            val isMockMode = credentialManager.isMockMode()

            if (!accountNo.isNullOrBlank()) {
                stockRepository.syncPortfolioFromApi(accountNo, accountProductCode, isMockMode)
                    .onFailure { Log.w(TAG, "포트폴리오 동기화 실패", it) }
            }

            // 보유 종목 분석
            val portfolios = stockRepository.getPortfolio()
            if (portfolios.isEmpty()) {
                Log.d(TAG, "보유 종목 없음")
                return Result.success()
            }

            Log.i(TAG, "보유 종목 ${portfolios.size}개 분석 시작")

            for (portfolio in portfolios) {
                try {
                    // 최신 주가 데이터 가져오기
                    val priceResult = stockRepository.fetchAndSavePriceData(portfolio.stockCode, 60)
                    if (priceResult.isFailure) {
                        Log.w(TAG, "[${portfolio.stockCode}] 주가 데이터 업데이트 실패")
                        continue
                    }

                    val priceData = priceResult.getOrThrow()
                    if (priceData.isEmpty()) continue

                    // 현재가로 포트폴리오 업데이트
                    val currentPrice = priceData.first().closePrice
                    stockRepository.updatePortfolioCurrentPrice(portfolio.stockCode, currentPrice)

                    // 매매 신호 생성 (보유 중 = portfolio 전달)
                    val signal = signalGenerator.generateSignal(priceData, portfolio)
                        ?: continue

                    Log.d(TAG, "[${portfolio.stockCode}] 신호: ${signal.signalType} (score=${signal.score})")

                    // 매수/매도 신호인 경우 주문 생성 및 알림
                    when (signal.signalType) {
                        SignalType.SELL, SignalType.STRONG_SELL -> {
                            // 매도 주문 생성 (PENDING, 사용자 확인 대기)
                            tradingRepository.createSellOrder(
                                stockCode = portfolio.stockCode,
                                stockName = portfolio.stockName,
                                quantity = portfolio.quantity,
                                price = currentPrice,
                                signalReason = signal.reasons.joinToString("; ")
                            ).onSuccess {
                                notificationManager.sendTradingSignalNotification(signal)
                                Log.i(TAG, "[${portfolio.stockCode}] 매도 신호 알림 전송")
                            }
                        }
                        SignalType.BUY, SignalType.STRONG_BUY -> {
                            // 보유 중인 종목 추가 매수는 신호만 전달 (알림)
                            notificationManager.sendTradingSignalNotification(signal)
                        }
                        SignalType.HOLD -> {
                            // HOLD: 알림 없음
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "[${portfolio.stockCode}] 분석 오류", e)
                }
            }

            Log.i(TAG, "보유 종목 분석 완료")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "AutoTradingWorker 오류", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    /**
     * 현재 시장 시간 여부 확인 (KST 09:00 ~ 15:30)
     */
    private fun isMarketOpen(): Boolean {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        // 주말 제외
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) return false

        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val openMinutes = MARKET_OPEN_HOUR * 60 + MARKET_OPEN_MINUTE
        val closeMinutes = MARKET_CLOSE_HOUR * 60 + MARKET_CLOSE_MINUTE

        return currentMinutes in openMinutes..closeMinutes
    }
}
