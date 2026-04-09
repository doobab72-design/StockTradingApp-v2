package com.stocktrading.work

import android.content.Context
import android.util.Log
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager 스케줄 관리자
 * DailyRecommendationWorker와 AutoTradingWorker의 등록/취소를 담당
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WorkScheduler"
    }

    /**
     * 모든 Worker 등록
     * 앱 시작 시 또는 설정 변경 시 호출
     */
    fun scheduleAll() {
        scheduleDailyRecommendation()
        scheduleAutoTrading()
        Log.i(TAG, "모든 Worker 스케줄 등록 완료")
    }

    /**
     * 일별 추천 Worker 등록 (매일 오전 8시)
     * PeriodicWorkRequest로 24시간 간격, 첫 실행 시간을 오전 8시로 맞춤
     */
    fun scheduleDailyRecommendation() {
        val initialDelay = calculateInitialDelay(targetHour = 8, targetMinute = 0)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<DailyRecommendationWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyRecommendationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        val delayMinutes = initialDelay / 60000
        Log.i(TAG, "DailyRecommendationWorker 등록: ${delayMinutes}분 후 첫 실행")
    }

    /**
     * 자동 거래 Worker 등록 (2시간마다)
     */
    fun scheduleAutoTrading() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<AutoTradingWorker>(
            repeatInterval = 2,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                15,
                TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AutoTradingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Log.i(TAG, "AutoTradingWorker 등록: 2시간마다 실행")
    }

    /**
     * 일별 추천 Worker 즉시 실행 (테스트/수동 트리거)
     */
    fun triggerDailyRecommendationNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DailyRecommendationWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.i(TAG, "DailyRecommendationWorker 즉시 실행 트리거")
    }

    /**
     * 자동 거래 Worker 즉시 실행 (테스트/수동 트리거)
     */
    fun triggerAutoTradingNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<AutoTradingWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.i(TAG, "AutoTradingWorker 즉시 실행 트리거")
    }

    /**
     * 모든 Worker 취소
     */
    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWork()
        Log.i(TAG, "모든 Worker 취소")
    }

    /**
     * 특정 Worker 취소
     */
    fun cancelDailyRecommendation() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(DailyRecommendationWorker.WORK_NAME)
    }

    fun cancelAutoTrading() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(AutoTradingWorker.WORK_NAME)
    }

    /**
     * 목표 시각까지의 지연 시간 계산 (밀리초)
     * 이미 지난 시각이면 다음 날의 해당 시각을 기준으로 계산
     */
    private fun calculateInitialDelay(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 이미 지난 시각이면 내일로 설정
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }
}
