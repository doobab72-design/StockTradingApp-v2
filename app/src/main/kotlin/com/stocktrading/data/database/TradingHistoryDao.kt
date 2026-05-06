package com.stocktrading.data.database

import androidx.room.*
import com.stocktrading.data.model.TradeStatus
import com.stocktrading.data.model.TradeType
import com.stocktrading.data.model.TradingRecord
import kotlinx.coroutines.flow.Flow

/**
 * 거래 내역 DAO
 */
@Dao
interface TradingHistoryDao {

    /**
     * 거래 내역 삽입
     */
    @Insert
    suspend fun insert(record: TradingRecord): Long

    /**
     * 거래 내역 업데이트
     */
    @Update
    suspend fun update(record: TradingRecord)

    /**
     * 전체 거래 내역 Flow (최신순)
     */
    @Query("SELECT * FROM trading_history ORDER BY tradedAt DESC")
    fun observeAllRecords(): Flow<List<TradingRecord>>

    /**
     * 최근 N개 거래 내역 조회
     */
    @Query("SELECT * FROM trading_history ORDER BY tradedAt DESC LIMIT :limit")
    suspend fun getRecentRecords(limit: Int = 50): List<TradingRecord>

    /**
     * 특정 종목 거래 내역 조회
     */
    @Query("SELECT * FROM trading_history WHERE stockCode = :stockCode ORDER BY tradedAt DESC")
    suspend fun getRecordsByStockCode(stockCode: String): List<TradingRecord>

    /**
     * 오늘의 거래 횟수 조회 (일일 최대 거래 10회 제한 체크)
     * @param dayStartMillis 오늘 자정 Unix timestamp
     */
    @Query("SELECT COUNT(*) FROM trading_history WHERE tradedAt >= :dayStartMillis AND status IN ('ORDERED', 'EXECUTED')")
    suspend fun getTodayTradeCount(dayStartMillis: Long): Int

    /**
     * 대기 중인 주문 목록 (사용자 확인 대기)
     */
    @Query("SELECT * FROM trading_history WHERE status = 'PENDING' ORDER BY tradedAt DESC")
    fun observePendingOrders(): Flow<List<TradingRecord>>

    /**
     * 대기 중인 주문 목록 (즉시 조회)
     */
    @Query("SELECT * FROM trading_history WHERE status = 'PENDING' ORDER BY tradedAt ASC")
    suspend fun getPendingOrders(): List<TradingRecord>

    /**
     * 주문 상태 업데이트
     */
    @Query("UPDATE trading_history SET status = :status, orderId = :orderId, executedAt = :executedAt WHERE id = :id")
    suspend fun updateOrderStatus(id: Long, status: TradeStatus, orderId: String? = null, executedAt: Long? = null)

    /**
     * 특정 ID의 거래 내역 조회
     */
    @Query("SELECT * FROM trading_history WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): TradingRecord?

    /**
     * 오늘의 매수 금액 합계 (1종목 최대 비중 5% 체크용)
     */
    @Query("SELECT SUM(totalAmount) FROM trading_history WHERE stockCode = :stockCode AND tradeType = 'BUY' AND tradedAt >= :dayStartMillis AND status IN ('ORDERED', 'EXECUTED')")
    suspend fun getTodayBuyAmountByStock(stockCode: String, dayStartMillis: Long): Double?

    /**
     * 기간별 거래 내역 조회
     */
    @Query("SELECT * FROM trading_history WHERE tradedAt BETWEEN :startMillis AND :endMillis ORDER BY tradedAt DESC")
    suspend fun getRecordsByDateRange(startMillis: Long, endMillis: Long): List<TradingRecord>
}
