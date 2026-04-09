package com.stocktrading.analysis

import android.util.Log
import com.stocktrading.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 매매 신호 생성기
 * RSI, MACD, 볼린저밴드, 이동평균, 주간패턴을 종합하여 매매 신호 생성
 * 최종 점수 범위: -100 ~ +100
 */
@Singleton
class TradingSignalGenerator @Inject constructor(
    private val weeklyPatternAnalyzer: WeeklyPatternAnalyzer
) {
    companion object {
        private const val TAG = "TradingSignalGenerator"

        // 각 지표별 가중치 (합계 = 100)
        private const val RSI_WEIGHT = 25.0
        private const val MACD_WEIGHT = 30.0
        private const val BOLLINGER_WEIGHT = 20.0
        private const val MA_WEIGHT = 15.0
        private const val PATTERN_WEIGHT = 10.0

        // 매매 신호 임계값
        private const val STRONG_BUY_THRESHOLD = 70.0
        private const val BUY_THRESHOLD = 40.0
        private const val SELL_THRESHOLD = -40.0
        private const val STRONG_SELL_THRESHOLD = -70.0
    }

    /**
     * 주가 데이터로부터 매매 신호 생성
     * @param priceDataList 최신 → 오래된 순 정렬된 주가 데이터 (최소 30개 필요)
     * @param portfolio 현재 보유 중인 경우 (null이면 미보유)
     */
    fun generateSignal(
        priceDataList: List<PriceData>,
        portfolio: Portfolio? = null
    ): TradingSignal? {
        if (priceDataList.size < 30) {
            Log.w(TAG, "[${priceDataList.firstOrNull()?.stockCode}] 데이터 부족: ${priceDataList.size}개")
            return null
        }

        val stockCode = priceDataList.first().stockCode
        val stockName = priceDataList.first().stockName
        val closePrices = priceDataList.map { it.closePrice }
        val currentPrice = closePrices.first()

        // ============================
        // 1. RSI 계산 및 점수화
        // ============================
        val rsiValue = TechnicalIndicators.rsi(closePrices, 14)
        val rsiScore = if (rsiValue != null) calculateRsiScore(rsiValue) else 0.0
        val rsiSignal = if (rsiValue != null) TechnicalIndicators.interpretRsi(rsiValue) else RsiSignal.NEUTRAL

        // ============================
        // 2. MACD 계산 및 점수화
        // ============================
        val macdResult = TechnicalIndicators.macd(closePrices)
        val macdScore = if (macdResult != null) calculateMacdScore(macdResult) else 0.0
        val macdSignal = if (macdResult != null) TechnicalIndicators.interpretMacd(macdResult) else MacdSignal.WEAKLY_BEARISH

        // ============================
        // 3. 볼린저밴드 계산 및 점수화
        // ============================
        val bollingerResult = TechnicalIndicators.bollingerBands(closePrices)
        val bollingerScore = if (bollingerResult != null) calculateBollingerScore(bollingerResult) else 0.0
        val bollingerSignal = if (bollingerResult != null) TechnicalIndicators.interpretBollinger(bollingerResult) else BollingerSignal.MIDDLE

        // ============================
        // 4. 이동평균 계산 및 점수화
        // ============================
        val maResult = TechnicalIndicators.movingAverages(closePrices)
        val maScore = calculateMaScore(maResult, currentPrice)

        // ============================
        // 5. 주간 패턴 점수화
        // ============================
        val patternResult = weeklyPatternAnalyzer.analyze(priceDataList)
        val patternScore = patternResult.score

        // ============================
        // 6. 종합 점수 계산 (가중 평균)
        // ============================
        val totalScore = (rsiScore * RSI_WEIGHT / 100) +
                (macdScore * MACD_WEIGHT / 100) +
                (bollingerScore * BOLLINGER_WEIGHT / 100) +
                (maScore * MA_WEIGHT / 100) +
                (patternScore * PATTERN_WEIGHT / 100)

        // 보유 중인 경우 손절매/익절매 강제 신호
        val adjustedScore = adjustScoreForPortfolio(totalScore, portfolio, currentPrice)

        val signalType = determineSignalType(adjustedScore)

        // ============================
        // 7. 신호 근거 생성
        // ============================
        val reasons = buildReasons(
            rsiValue, rsiSignal, macdResult, macdSignal,
            bollingerResult, bollingerSignal, maResult,
            patternResult, portfolio, currentPrice
        )

        val indicators = IndicatorSnapshot(
            rsi = rsiValue ?: 50.0,
            macd = macdResult?.macd ?: 0.0,
            macdSignal = macdResult?.signal ?: 0.0,
            macdHistogram = macdResult?.histogram ?: 0.0,
            bollingerUpper = bollingerResult?.upper ?: currentPrice * 1.02,
            bollingerMiddle = bollingerResult?.middle ?: currentPrice,
            bollingerLower = bollingerResult?.lower ?: currentPrice * 0.98,
            ma5 = maResult.ma5 ?: currentPrice,
            ma20 = maResult.ma20 ?: currentPrice,
            ma60 = maResult.ma60 ?: currentPrice,
            currentPrice = currentPrice
        )

        // 추천 수량 계산 (총 자산의 1% 이내, 간소화된 계산)
        val recommendedQuantity = calculateRecommendedQuantity(currentPrice)

        Log.d(TAG, "[$stockCode] 신호 생성: type=$signalType, score=$adjustedScore (RSI=${rsiScore}, MACD=${macdScore}, BB=${bollingerScore})")

        return TradingSignal(
            stockCode = stockCode,
            stockName = stockName,
            signalType = signalType,
            recommendedQuantity = recommendedQuantity,
            recommendedPrice = currentPrice,
            score = adjustedScore,
            reasons = reasons,
            indicators = indicators,
            riskLevel = determineRiskLevel(adjustedScore, rsiValue, bollingerResult)
        )
    }

    /**
     * 추천 종목 생성 (신규 매수 후보)
     */
    fun generateRecommendation(priceDataList: List<PriceData>): RecommendedStock? {
        val signal = generateSignal(priceDataList) ?: return null

        if (signal.signalType !in listOf(SignalType.BUY, SignalType.STRONG_BUY)) return null

        val currentPrice = signal.recommendedPrice
        return RecommendedStock(
            stockCode = signal.stockCode,
            stockName = signal.stockName,
            currentPrice = currentPrice,
            targetPrice = currentPrice * (1 + Portfolio.TAKE_PROFIT_RATE),
            stopLossPrice = currentPrice * (1 + Portfolio.STOP_LOSS_RATE),
            expectedReturnRate = Portfolio.TAKE_PROFIT_RATE * 100,
            recommendReason = signal.reasons.take(3).joinToString(", "),
            score = signal.score,
            indicators = signal.indicators
        )
    }

    // ============================
    // 점수화 함수
    // ============================

    private fun calculateRsiScore(rsi: Double): Double {
        return when {
            rsi <= 20 -> 100.0   // 극도 과매도
            rsi <= 30 -> 80.0    // 과매도
            rsi <= 40 -> 40.0    // 약한 매수
            rsi <= 60 -> 0.0     // 중립
            rsi <= 70 -> -40.0   // 약한 매도
            rsi <= 80 -> -80.0   // 과매수
            else -> -100.0       // 극도 과매수
        }
    }

    private fun calculateMacdScore(macd: MacdResult): Double {
        val histogramScore = when {
            macd.histogram > 0 && (macd.prevHistogram ?: 0.0) < 0 -> 100.0   // 골든크로스
            macd.histogram < 0 && (macd.prevHistogram ?: 0.0) > 0 -> -100.0  // 데드크로스
            macd.histogram > 0 && macd.macd > 0 -> 60.0     // 상승 추세 강화
            macd.histogram > 0 && macd.macd <= 0 -> 30.0    // 약한 반등
            macd.histogram < 0 && macd.macd < 0 -> -60.0    // 하락 추세 강화
            macd.histogram < 0 && macd.macd >= 0 -> -30.0   // 약한 하락
            else -> 0.0
        }
        return histogramScore
    }

    private fun calculateBollingerScore(bb: BollingerResult): Double {
        return when {
            bb.percentB <= 0.0 -> 100.0    // 하단 밴드 이탈
            bb.percentB <= 0.1 -> 80.0
            bb.percentB <= 0.2 -> 50.0
            bb.percentB <= 0.4 -> 20.0
            bb.percentB <= 0.6 -> 0.0
            bb.percentB <= 0.8 -> -20.0
            bb.percentB <= 0.9 -> -50.0
            bb.percentB <= 1.0 -> -80.0
            else -> -100.0                  // 상단 밴드 이탈
        }
    }

    private fun calculateMaScore(ma: MovingAverageResult, currentPrice: Double): Double {
        var score = 0.0

        // 정배열 여부
        if (ma.isUptrend()) score += 50.0
        else if (ma.isDowntrend()) score -= 50.0

        // 현재가와 이동평균 관계
        val ma20 = ma.ma20
        if (ma20 != null) {
            val diffRate = (currentPrice - ma20) / ma20 * 100
            score += when {
                diffRate > 10 -> -30.0  // 이평 대비 10% 이상 과열
                diffRate > 5 -> -10.0
                diffRate in -5.0..5.0 -> 10.0   // 이평 근접 = 안정적
                diffRate < -10 -> 30.0   // 이평 대비 10% 이상 과소
                else -> 0.0
            }
        }

        return score.coerceIn(-100.0, 100.0)
    }

    private fun adjustScoreForPortfolio(
        score: Double,
        portfolio: Portfolio?,
        currentPrice: Double
    ): Double {
        if (portfolio == null) return score

        // 손절매 조건: 현재가가 손절매 기준가 이하
        if (currentPrice <= portfolio.stopLossPrice) {
            Log.w("TradingSignalGenerator", "[${portfolio.stockCode}] 손절매 조건 달성: $currentPrice <= ${portfolio.stopLossPrice}")
            return -100.0
        }

        // 익절매 조건: 현재가가 익절매 기준가 이상
        if (currentPrice >= portfolio.takeProfitPrice) {
            Log.i("TradingSignalGenerator", "[${portfolio.stockCode}] 익절매 조건 달성: $currentPrice >= ${portfolio.takeProfitPrice}")
            return -80.0
        }

        return score
    }

    private fun determineSignalType(score: Double): SignalType {
        return when {
            score >= STRONG_BUY_THRESHOLD -> SignalType.STRONG_BUY
            score >= BUY_THRESHOLD -> SignalType.BUY
            score <= STRONG_SELL_THRESHOLD -> SignalType.STRONG_SELL
            score <= SELL_THRESHOLD -> SignalType.SELL
            else -> SignalType.HOLD
        }
    }

    private fun determineRiskLevel(
        score: Double,
        rsi: Double?,
        bollinger: BollingerResult?
    ): RiskLevel {
        var riskPoints = 0

        // RSI 극단값은 고위험
        if (rsi != null && (rsi < 25 || rsi > 75)) riskPoints++

        // 볼린저 밴드 이탈은 고위험
        if (bollinger != null && (bollinger.percentB < 0 || bollinger.percentB > 1)) riskPoints++

        // 점수 극단값
        if (kotlin.math.abs(score) > 80) riskPoints++

        return when (riskPoints) {
            0 -> RiskLevel.LOW
            1 -> RiskLevel.MEDIUM
            else -> RiskLevel.HIGH
        }
    }

    private fun calculateRecommendedQuantity(price: Double): Int {
        // 기본 추천: 100,000원 / 현재가 (최소 1주)
        return maxOf(1, (100_000 / price).toInt())
    }

    private fun buildReasons(
        rsi: Double?,
        rsiSignal: RsiSignal,
        macd: MacdResult?,
        macdSignal: MacdSignal,
        bollinger: BollingerResult?,
        bollingerSignal: BollingerSignal,
        ma: MovingAverageResult,
        pattern: WeeklyPatternResult,
        portfolio: Portfolio?,
        currentPrice: Double
    ): List<String> {
        val reasons = mutableListOf<String>()

        // RSI 근거
        rsi?.let {
            reasons.add(when (rsiSignal) {
                RsiSignal.OVERSOLD -> "RSI ${String.format("%.1f", it)} 과매도 구간 (매수 신호)"
                RsiSignal.OVERBOUGHT -> "RSI ${String.format("%.1f", it)} 과매수 구간 (매도 신호)"
                RsiSignal.NEAR_OVERSOLD -> "RSI ${String.format("%.1f", it)} 과매도 근접"
                RsiSignal.NEAR_OVERBOUGHT -> "RSI ${String.format("%.1f", it)} 과매수 근접"
                else -> "RSI ${String.format("%.1f", it)} 중립"
            })
        }

        // MACD 근거
        macd?.let {
            reasons.add(when (macdSignal) {
                MacdSignal.GOLDEN_CROSS -> "MACD 골든크로스 발생 (강력 매수)"
                MacdSignal.DEAD_CROSS -> "MACD 데드크로스 발생 (강력 매도)"
                MacdSignal.BULLISH -> "MACD 상승 추세 강화"
                MacdSignal.BEARISH -> "MACD 하락 추세 강화"
                else -> "MACD 중립"
            })
        }

        // 볼린저밴드 근거
        bollinger?.let {
            reasons.add(when (bollingerSignal) {
                BollingerSignal.BELOW_LOWER -> "볼린저밴드 하단 이탈 - 과매도 반등 기대"
                BollingerSignal.ABOVE_UPPER -> "볼린저밴드 상단 이탈 - 과매수 조정 예상"
                BollingerSignal.NEAR_LOWER -> "볼린저밴드 하단 근접"
                BollingerSignal.NEAR_UPPER -> "볼린저밴드 상단 근접"
                else -> "볼린저밴드 중간 구간"
            })
        }

        // 이동평균 근거
        if (ma.isUptrend()) reasons.add("5/20/60일 이동평균 정배열 (상승 추세)")
        else if (ma.isDowntrend()) reasons.add("5/20/60일 이동평균 역배열 (하락 추세)")

        // 손절/익절 근거
        portfolio?.let {
            if (currentPrice <= it.stopLossPrice) {
                reasons.add(0, "⚠️ 손절매 조건 도달: 손실 ${String.format("%.1f", it.profitRate)}%")
            } else if (currentPrice >= it.takeProfitPrice) {
                reasons.add(0, "✅ 익절매 조건 도달: 수익 ${String.format("%.1f", it.profitRate)}%")
            }
        }

        return reasons.take(5)  // 최대 5개 근거
    }
}
