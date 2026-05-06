package com.stocktrading.data.model

/**
 * 매매 신호 모델
 * TradingSignalGenerator가 생성하고 사용자에게 확인을 요청하는 단위
 */
data class TradingSignal(
    val stockCode: String,
    val stockName: String,

    /** 신호 유형 */
    val signalType: SignalType,

    /** 추천 수량 */
    val recommendedQuantity: Int,

    /** 추천 가격 (시장가 주문 시 현재가 기준) */
    val recommendedPrice: Double,

    /** 종합 신호 점수 (-100 ~ +100) */
    val score: Double,

    /** 신호 생성 근거 상세 */
    val reasons: List<String>,

    /** 기술적 지표 스냅샷 */
    val indicators: IndicatorSnapshot,

    /** 신호 생성 시각 */
    val generatedAt: Long = System.currentTimeMillis(),

    /** 위험 수준 */
    val riskLevel: RiskLevel = RiskLevel.MEDIUM
)

enum class SignalType {
    STRONG_BUY,   // 강력 매수 (점수 >= 70)
    BUY,          // 매수 (점수 >= 40)
    HOLD,         // 보유 (-40 < 점수 < 40)
    SELL,         // 매도 (점수 <= -40)
    STRONG_SELL   // 강력 매도 (점수 <= -70)
}

enum class RiskLevel {
    LOW, MEDIUM, HIGH
}

/**
 * 기술적 지표 스냅샷 (신호 생성 시점의 값)
 */
data class IndicatorSnapshot(
    val rsi: Double,
    val macd: Double,
    val macdSignal: Double,
    val macdHistogram: Double,
    val bollingerUpper: Double,
    val bollingerMiddle: Double,
    val bollingerLower: Double,
    val ma5: Double,
    val ma20: Double,
    val ma60: Double,
    val currentPrice: Double
)

/**
 * 추천 종목 모델 (오전 8시 푸시 알림용)
 */
data class RecommendedStock(
    val stockCode: String,
    val stockName: String,
    val currentPrice: Double,
    val targetPrice: Double,
    val stopLossPrice: Double,
    val expectedReturnRate: Double,
    val recommendReason: String,
    val score: Double,
    val indicators: IndicatorSnapshot,
    val recommendedAt: Long = System.currentTimeMillis()
)
