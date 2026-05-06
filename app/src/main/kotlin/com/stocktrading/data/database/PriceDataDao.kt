package com.stocktrading.data.database

import androidx.room.*
import com.stocktrading.data.model.PriceData
import kotlinx.coroutines.flow.Flow

/**
 * 주가 데이터 DAO
 * 일별 주가 데이터의 CRUD 및 분석용 쿼리 제공
 */
@Dao
interface PriceDataDao {

    /**
     * 단일 주가 데이터 삽입 (기존 데이터 충돌 시 교체)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(priceData: PriceData)

    /**
     * 여러 주가 데이터 일괄 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(priceDataList: List<PriceData>)

    /**
     * 특정 종목의 최근 N일 주가 데이터 조회 (날짜 내림차순)
     */
    @Query("SELECT * FROM price_data WHERE stockCode = :stockCode ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentPriceData(stockCode: String, limit: Int = 60): List<PriceData>

    /**
     * 특정 종목의 최근 N일 주가 데이터 Flow (실시간 관찰)
     */
    @Query("SELECT * FROM price_data WHERE stockCode = :stockCode ORDER BY date DESC LIMIT :limit")
    fun observeRecentPriceData(stockCode: String, limit: Int = 60): Flow<List<PriceData>>

    /**
     * 특정 종목의 특정 날짜 주가 데이터 조회
     */
    @Query("SELECT * FROM price_data WHERE stockCode = :stockCode AND date = :date LIMIT 1")
    suspend fun getPriceDataByDate(stockCode: String, date: String): PriceData?

    /**
     * 특정 종목의 특정 기간 주가 데이터 조회
     */
    @Query("SELECT * FROM price_data WHERE stockCode = :stockCode AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getPriceDataByDateRange(stockCode: String, startDate: String, endDate: String): List<PriceData>

    /**
     * 특정 종목의 가장 최근 주가 데이터 조회
     */
    @Query("SELECT * FROM price_data WHERE stockCode = :stockCode ORDER BY date DESC LIMIT 1")
    suspend fun getLatestPriceData(stockCode: String): PriceData?

    /**
     * 오래된 주가 데이터 삭제 (N일 이전)
     */
    @Query("DELETE FROM price_data WHERE stockCode = :stockCode AND date < :beforeDate")
    suspend fun deleteOldPriceData(stockCode: String, beforeDate: String): Int

    /**
     * 특정 종목의 모든 주가 데이터 삭제
     */
    @Query("DELETE FROM price_data WHERE stockCode = :stockCode")
    suspend fun deleteAllByStockCode(stockCode: String)

    /**
     * DB에 저장된 종목 코드 목록 조회
     */
    @Query("SELECT DISTINCT stockCode FROM price_data")
    suspend fun getAllStockCodes(): List<String>

    /**
     * 종목 코드별 가장 최근 날짜 조회
     */
    @Query("SELECT MAX(date) FROM price_data WHERE stockCode = :stockCode")
    suspend fun getLatestDate(stockCode: String): String?
}
