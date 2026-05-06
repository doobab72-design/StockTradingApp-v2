package com.stocktrading

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject

/**
 * Hilt Worker를 사용하려면 WorkManager를 수동 초기화해야 합니다.
 * - AndroidManifest에서 WorkManagerInitializer를 tools:node="remove"로 비활성화
 * - 대신 이 클래스에서 HiltWorkerFactory를 주입받아 초기화
 */
@HiltAndroidApp
class StockTradingApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
