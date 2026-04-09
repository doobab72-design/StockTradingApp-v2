package com.stocktrading.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.stocktrading.analysis.TradingSignalGenerator
import com.stocktrading.data.repository.StockRepository
import com.stocktrading.notification.TradingNotificationManager
import com.stocktrading.security.SecureCredentialManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 매일 오전 8시 실행되는 추천 종목 분석 Worker
 * - KOSPI 200 내 주요 종목을 분석하여 상위 3개 추천
 * - 추천 결과를 푸시 알림으로 전송
 */
@HiltWorker
class DailyRecommendationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val stockRepository: StockRepository,
    private val signalGenerator: TradingSignalGenerator,
    private val notificationManager: TradingNotificationManager,
    private val credentialManager: SecureCredentialManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DailyRecommendationWorker"
        const val WORK_NAME = "daily_recommendation"

        /**
         * 분석 대상 후보 종목 (KOSPI 200 주요 종목)
         * 실제 운영 시 더 많은 종목 추가 가능
         */
        val CANDIDATE_STOCKS = listOf(
            Pair("005930", "삼성전자"),
            Pair("000660", "SK하이닉스"),
            Pair("035420", "NAVER"),
            Pair("035720", "카카오"),
            Pair("051910", "LG화학"),
            Pair("006400", "삼성SDI"),
            Pair("207940", "삼성바이오로직스"),
            Pair("028260", "삼성물산"),
            Pair("105560", "KB금융"),
            Pair("055550", "신한지주"),
            Pair("012330", "현대모비스"),
            Pair("066570", "LG전자"),
            Pair("003550", "LG"),
            Pair("096770", "SK이노베이션"),
            Pair("034730", "SK"),
        )
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "일별 추천 종목 분석 시작")

        // API 키 설정 확인
        if (!credentialManager.isApiKeyConfigured()) {
            Log.w(TAG, "API 키 미설정 - 분석 건너뜀")
            notificationManager.sendSetupRequiredNotification()
            return Result.success()
        }

        return try {
            val recommendations = analyzeAndRecommend()

            if (recommendations.isNotEmpty()) {
                notificationManager.sendRecommendationNotification(recommendations)
                Log.i(TAG, "추천 종목 알림 전송: ${recommendations.map { it.stockName }}")
            } else {
                Log.i(TAG, "이날 추천 종목 없음 (매수 조건 미충족)")
            }

            // 오래된 주가 데이터 정리
            stockRepository.cleanupOldPriceData()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "추천 분석 오류", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    /**
     * 후보 종목 분석 후 상위 3개 추천 반환
     */
    private suspend fun analyzeAndRecommend() = buildList {
        for ((code, _) in CANDIDATE_STOCKS) {
            try {
                val priceResult = stockRepository.getPriceData(code, 60)
                if (priceResult.isFailure) {
                    Log.w(TAG, "[$code] 주가 데이터 조회 실패, 건너뜀")
                    continue
                }

                val priceData = priceResult.getOrThrow()
                if (priceData.size < 30) {
                    Log.w(TAG, "[$code] 데이터 부족 (${priceData.size}개)")
                    continue
                }

                val recommendation = signalGenerator.generateRecommendation(priceData)
                if (recommendation != null) {
                    add(recommendation)
                    Log.d(TAG, "[$code] 추천 종목 추가: score=${recommendation.score}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[$code] 분석 오류", e)
            }
        }
    }.sortedByDescending { it.score }.take(3)
}
