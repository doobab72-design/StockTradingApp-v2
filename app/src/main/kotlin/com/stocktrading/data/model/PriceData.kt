package com.stocktrading.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 일별 주가 데이터 모델
 * KIS API의 /uapi/domestic-stock/v1/quotations/inquire-daily-price 응답 매핑
 */
@Entity(tableName = "price_data")
data class PriceData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 종목 코드 (예: 005930 = 삼성전자) */
    val stockCode: String,

    /** 종목명 */
    val stockName: String,

    /** 기준일자 (yyyyMMdd 형식) */
    val date: String,

    /** 시가 */
    val openPrice: Double,

    /** 고가 */
    val highPrice: Double,

    /** 저가 */
    val lowPrice: Double,

    /** 종가 */
    val closePrice: Double,

    /** 거래량 */
    val volume: Long,

    /** 전일 대비 변동액 */
    val priceChange: Double,

    /** 전일 대비 변동률 (%) */
    val changeRate: Double,

    /** 데이터 저장 시각 (Unix timestamp) */
    val savedAt: Long = System.currentTimeMillis()
)

/**
 * 실시간 시세 데이터 (API 응답용, DB 비저장)
 */
data class CurrentPrice(
    val stockCode: String,
    val stockName: String,
    val currentPrice: Double,
    val changeAmount: Double,
    val changeRate: Double,
    val volume: Long,
    val marketCap: Long = 0L
)

/**
 * KIS API 일별 주가 응답 DTO
 */
data class DailyPriceResponse(
    val rt_cd: String?,       // 응답 코드 (0: 성공)
    val msg_cd: String?,      // 메시지 코드
    val msg1: String?,        // 메시지
    val output2: List<DailyPriceOutput>?  // null 가능 (API 오류 시)
)

data class DailyPriceOutput(
    val stck_bsop_date: String,  // 주식 영업 일자
    val stck_oprc: String,        // 시가
    val stck_hgpr: String,        // 고가
    val stck_lwpr: String,        // 저가
    val stck_clpr: String,        // 종가
    val acml_vol: String,         // 누적 거래량
    val prdy_vrss: String,        // 전일 대비
    val prdy_ctrt: String         // 전일 대비율
)
