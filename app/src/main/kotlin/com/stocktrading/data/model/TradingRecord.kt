package com.stocktrading.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 거래 내역 기록 모델
 */
@Entity(tableName = "trading_history")
data class TradingRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 종목 코드 */
    val stockCode: String,

    /** 종목명 */
    val stockName: String,

    /** 거래 유형 (BUY / SELL) */
    val tradeType: TradeType,

    /** 주문 수량 */
    val quantity: Int,

    /** 주문 단가 */
    val price: Double,

    /** 총 거래 금액 */
    val totalAmount: Double,

    /** 거래 상태 */
    val status: TradeStatus,

    /** KIS 주문 번호 (API 응답) */
    val orderId: String? = null,

    /** 매매 신호 기반 근거 */
    val signalReason: String = "",

    /** 거래 일시 (Unix timestamp) */
    val tradedAt: Long = System.currentTimeMillis(),

    /** 체결 일시 */
    val executedAt: Long? = null
)

enum class TradeType {
    BUY, SELL
}

enum class TradeStatus {
    PENDING,    // 주문 대기 (사용자 확인 전)
    ORDERED,    // 주문 완료
    EXECUTED,   // 체결 완료
    CANCELLED,  // 취소됨
    FAILED      // 실패
}

/**
 * KIS API 주문 요청 DTO
 */
data class OrderRequest(
    val CANO: String,           // 계좌번호 (앞 8자리)
    val ACNT_PRDT_CD: String,   // 계좌상품코드 (뒤 2자리)
    val PDNO: String,           // 종목 코드
    val ORD_DVSN: String,       // 주문구분 (00: 지정가, 01: 시장가)
    val ORD_QTY: String,        // 주문 수량
    val ORD_UNPR: String        // 주문 단가 (시장가는 "0")
)

/**
 * KIS API 주문 응답 DTO
 */
data class OrderResponse(
    val rt_cd: String?,
    val msg_cd: String?,
    val msg1: String?,
    val output: OrderOutput?
)

data class OrderOutput(
    val KRX_FWDG_ORD_ORGNO: String?,  // 한국거래소 전송 주문 조직 번호
    val ORNO: String?,                  // 주문 번호
    val ORD_TMD: String?               // 주문 시각
)
