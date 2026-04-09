package com.stocktrading.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 보유 종목 (포트폴리오) 모델
 * KIS API의 inquire-balance 응답 + 로컬 관리 정보
 */
@Entity(tableName = "portfolio")
data class Portfolio(
    @PrimaryKey
    val stockCode: String,

    /** 종목명 */
    val stockName: String,

    /** 보유 수량 */
    val quantity: Int,

    /** 평균 매수가 */
    val averageBuyPrice: Double,

    /** 현재가 (마지막 조회 시점) */
    val currentPrice: Double,

    /** 매입 금액 합계 */
    val totalBuyAmount: Double,

    /** 평가 금액 */
    val evaluationAmount: Double,

    /** 평가 손익 */
    val profitLoss: Double,

    /** 수익률 (%) */
    val profitRate: Double,

    /** 손절매 기준가 (-5%) */
    val stopLossPrice: Double,

    /** 익절매 기준가 (+10%) */
    val takeProfitPrice: Double,

    /** 마지막 업데이트 시각 */
    val lastUpdatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** 손절매 비율 (-5%) */
        const val STOP_LOSS_RATE = -0.05

        /** 익절매 비율 (+10%) */
        const val TAKE_PROFIT_RATE = 0.10

        fun create(
            stockCode: String,
            stockName: String,
            quantity: Int,
            buyPrice: Double,
            currentPrice: Double
        ): Portfolio {
            val totalBuyAmount = buyPrice * quantity
            val evaluationAmount = currentPrice * quantity
            val profitLoss = evaluationAmount - totalBuyAmount
            val profitRate = if (totalBuyAmount > 0) (profitLoss / totalBuyAmount) * 100 else 0.0

            return Portfolio(
                stockCode = stockCode,
                stockName = stockName,
                quantity = quantity,
                averageBuyPrice = buyPrice,
                currentPrice = currentPrice,
                totalBuyAmount = totalBuyAmount,
                evaluationAmount = evaluationAmount,
                profitLoss = profitLoss,
                profitRate = profitRate,
                stopLossPrice = buyPrice * (1 + STOP_LOSS_RATE),
                takeProfitPrice = buyPrice * (1 + TAKE_PROFIT_RATE)
            )
        }
    }
}

/**
 * KIS API 잔고 조회 응답 DTO
 */
data class BalanceResponse(
    val rt_cd: String?,
    val msg_cd: String?,
    val msg1: String?,
    // KIS API는 보유 종목이 없을 때 output1을 null 또는 빈 배열로 반환할 수 있음
    val output1: List<BalanceOutput1>?,
    val output2: List<BalanceOutput2>?
)

data class BalanceOutput1(
    val pdno: String?,           // 종목 코드
    val prdt_name: String?,      // 종목명
    val hldg_qty: String?,       // 보유 수량
    val pchs_avg_pric: String?,  // 매수 평균가
    val prpr: String?,           // 현재가
    val pchs_amt: String?,       // 매입 금액
    val evlu_amt: String?,       // 평가 금액
    val evlu_pfls_amt: String?,  // 평가 손익
    val evlu_pfls_rt: String?    // 평가 수익률
)

data class BalanceOutput2(
    val pchs_amt_smtl_amt: String?,   // 매입 금액 합계
    val evlu_amt_smtl_amt: String?,   // 평가 금액 합계
    val evlu_pfls_smtl_amt: String?,  // 평가 손익 합계
    val tot_evlu_amt: String?         // 총 평가 금액
)
