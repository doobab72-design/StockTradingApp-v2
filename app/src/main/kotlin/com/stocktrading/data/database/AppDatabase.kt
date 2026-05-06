package com.stocktrading.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.stocktrading.data.model.Portfolio
import com.stocktrading.data.model.PriceData
import com.stocktrading.data.model.TradeStatus
import com.stocktrading.data.model.TradeType
import com.stocktrading.data.model.TradingRecord

/**
 * Room 데이터베이스 정의
 * 앱의 모든 로컬 데이터를 관리
 */
@Database(
    entities = [
        PriceData::class,
        Portfolio::class,
        TradingRecord::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun priceDataDao(): PriceDataDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun tradingHistoryDao(): TradingHistoryDao
}

/**
 * Room TypeConverter - Enum 타입 변환
 */
class AppTypeConverters {

    @TypeConverter
    fun tradeTypeToString(tradeType: TradeType): String = tradeType.name

    @TypeConverter
    fun stringToTradeType(value: String): TradeType = TradeType.valueOf(value)

    @TypeConverter
    fun tradeStatusToString(tradeStatus: TradeStatus): String = tradeStatus.name

    @TypeConverter
    fun stringToTradeStatus(value: String): TradeStatus = TradeStatus.valueOf(value)
}
