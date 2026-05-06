package com.stocktrading.di

import android.content.Context
import androidx.room.Room
import com.stocktrading.analysis.WeeklyPatternAnalyzer
import com.stocktrading.data.database.AppDatabase
import com.stocktrading.data.database.PortfolioDao
import com.stocktrading.data.database.PriceDataDao
import com.stocktrading.data.database.TradingHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI 모듈
 * 앱 전역에서 사용되는 의존성 제공
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Room 데이터베이스 인스턴스 제공
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "stock_trading.db"
        )
            .fallbackToDestructiveMigration()  // 개발 단계: 스키마 변경 시 재생성
            .build()
    }

    @Provides
    @Singleton
    fun providePriceDataDao(db: AppDatabase): PriceDataDao = db.priceDataDao()

    @Provides
    @Singleton
    fun providePortfolioDao(db: AppDatabase): PortfolioDao = db.portfolioDao()

    @Provides
    @Singleton
    fun provideTradingHistoryDao(db: AppDatabase): TradingHistoryDao = db.tradingHistoryDao()

    @Provides
    @Singleton
    fun provideWeeklyPatternAnalyzer(): WeeklyPatternAnalyzer = WeeklyPatternAnalyzer()
}
