package com.stocktrading.data.database

import androidx.room.*
import com.stocktrading.data.model.Portfolio
import kotlinx.coroutines.flow.Flow

/**
 * 보유 종목(포트폴리오) DAO
 */
@Dao
interface PortfolioDao {

    /**
     * 보유 종목 삽입 또는 업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(portfolio: Portfolio)

    /**
     * 여러 보유 종목 일괄 삽입/업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(portfolios: List<Portfolio>)

    /**
     * 전체 보유 종목 목록 Flow (실시간 관찰)
     */
    @Query("SELECT * FROM portfolio ORDER BY evaluationAmount DESC")
    fun observeAllPortfolios(): Flow<List<Portfolio>>

    /**
     * 전체 보유 종목 목록 조회
     */
    @Query("SELECT * FROM portfolio ORDER BY evaluationAmount DESC")
    suspend fun getAllPortfolios(): List<Portfolio>

    /**
     * 특정 종목 보유 정보 조회
     */
    @Query("SELECT * FROM portfolio WHERE stockCode = :stockCode LIMIT 1")
    suspend fun getPortfolioByCode(stockCode: String): Portfolio?

    /**
     * 특정 종목 보유 정보 Flow
     */
    @Query("SELECT * FROM portfolio WHERE stockCode = :stockCode LIMIT 1")
    fun observePortfolioByCode(stockCode: String): Flow<Portfolio?>

    /**
     * 보유 종목 삭제 (전량 매도 시)
     */
    @Query("DELETE FROM portfolio WHERE stockCode = :stockCode")
    suspend fun deleteByStockCode(stockCode: String)

    /**
     * 전체 보유 종목 삭제 (동기화 시)
     */
    @Query("DELETE FROM portfolio")
    suspend fun deleteAll()

    /**
     * 총 평가 금액 합계
     */
    @Query("SELECT SUM(evaluationAmount) FROM portfolio")
    suspend fun getTotalEvaluationAmount(): Double?

    /**
     * 총 매입 금액 합계
     */
    @Query("SELECT SUM(totalBuyAmount) FROM portfolio")
    suspend fun getTotalBuyAmount(): Double?

    /**
     * 손절매 조건 도달한 종목 조회 (currentPrice <= stopLossPrice)
     */
    @Query("SELECT * FROM portfolio WHERE currentPrice <= stopLossPrice")
    suspend fun getStopLossTriggeredPortfolios(): List<Portfolio>

    /**
     * 익절매 조건 도달한 종목 조회 (currentPrice >= takeProfitPrice)
     */
    @Query("SELECT * FROM portfolio WHERE currentPrice >= takeProfitPrice")
    suspend fun getTakeProfitTriggeredPortfolios(): List<Portfolio>

    /**
     * 현재가 업데이트
     */
    @Query("UPDATE portfolio SET currentPrice = :currentPrice, evaluationAmount = :evaluationAmount, profitLoss = :profitLoss, profitRate = :profitRate, lastUpdatedAt = :updatedAt WHERE stockCode = :stockCode")
    suspend fun updateCurrentPrice(
        stockCode: String,
        currentPrice: Double,
        evaluationAmount: Double,
        profitLoss: Double,
        profitRate: Double,
        updatedAt: Long = System.currentTimeMillis()
    )
}
