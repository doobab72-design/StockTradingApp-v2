package com.stocktrading.analysis

import kotlin.math.sqrt

/**
 * 기술적 지표 계산기
 * RSI, MACD, 볼린저밴드, 이동평균 계산
 */
object TechnicalIndicators {

    // ============================
    // RSI (Relative Strength Index)
    // ============================

    fun rsi(prices: List<Double>, period: Int = 14): Double? {
        if (prices.size < period + 1) return null

        val changes = prices.zipWithNext { a, b -> b - a }.reversed()

        var avgGain = changes.take(period).filter { it > 0 }.sum() / period
        var avgLoss = changes.take(period).filter { it < 0 }.map { -it }.sum() / period

        for (i in period until changes.size) {
            val change = changes[i]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
        }

        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    fun interpretRsi(rsi: Double): RsiSignal = when {
        rsi <= 30 -> RsiSignal.OVERSOLD
        rsi <= 40 -> RsiSignal.NEAR_OVERSOLD
        rsi >= 70 -> RsiSignal.OVERBOUGHT
        rsi >= 60 -> RsiSignal.NEAR_OVERBOUGHT
        else -> RsiSignal.NEUTRAL
    }

    // ============================
    // MACD
    // ============================

    fun macd(prices: List<Double>, fastPeriod: Int = 12, slowPeriod: Int = 26, signalPeriod: Int = 9): MacdResult? {
        if (prices.size < slowPeriod + signalPeriod) return null

        val reversed = prices.reversed()
        val fastEma = ema(reversed, fastPeriod) ?: return null
        val slowEma = ema(reversed, slowPeriod) ?: return null

        val macdLine = fastEma - slowEma

        val macdHistory = mutableListOf<Double>()
        for (i in reversed.indices) {
            val fe = emaAt(reversed, fastPeriod, i) ?: continue
            val se = emaAt(reversed, slowPeriod, i) ?: continue
            macdHistory.add(fe - se)
        }

        if (macdHistory.size < signalPeriod) return null

        val signalLine = emaList(macdHistory, signalPeriod) ?: return null
        val histogram = macdLine - signalLine

        val prevHistogram = if (macdHistory.size >= signalPeriod + 1) {
            val prevSignal = emaList(macdHistory.drop(1), signalPeriod) ?: signalLine
            macdHistory[1] - prevSignal
        } else null

        return MacdResult(
            macd = macdLine,
            signal = signalLine,
            histogram = histogram,
            prevHistogram = prevHistogram
        )
    }

    fun interpretMacd(result: MacdResult): MacdSignal {
        val prev = result.prevHistogram ?: 0.0
        return when {
            result.histogram > 0 && prev < 0 -> MacdSignal.GOLDEN_CROSS
            result.histogram < 0 && prev > 0 -> MacdSignal.DEAD_CROSS
            result.histogram > 0 && result.macd > 0 -> MacdSignal.BULLISH
            result.histogram > 0 -> MacdSignal.WEAKLY_BULLISH
            result.histogram < 0 && result.macd < 0 -> MacdSignal.BEARISH
            else -> MacdSignal.WEAKLY_BEARISH
        }
    }

    // ============================
    // 볼린저 밴드
    // ============================

    fun bollingerBands(prices: List<Double>, period: Int = 20, multiplier: Double = 2.0): BollingerResult? {
        if (prices.size < period) return null

        val slice = prices.take(period)
        val middle = slice.average()
        val stdDev = sqrt(slice.map { (it - middle) * (it - middle) }.average())

        val upper = middle + multiplier * stdDev
        val lower = middle - multiplier * stdDev
        val currentPrice = prices.first()

        val percentB = if (upper != lower) (currentPrice - lower) / (upper - lower) else 0.5

        return BollingerResult(
            upper = upper,
            middle = middle,
            lower = lower,
            percentB = percentB
        )
    }

    fun interpretBollinger(result: BollingerResult): BollingerSignal = when {
        result.percentB < 0.0 -> BollingerSignal.BELOW_LOWER
        result.percentB < 0.2 -> BollingerSignal.NEAR_LOWER
        result.percentB > 1.0 -> BollingerSignal.ABOVE_UPPER
        result.percentB > 0.8 -> BollingerSignal.NEAR_UPPER
        else -> BollingerSignal.MIDDLE
    }

    // ============================
    // 이동평균
    // ============================

    fun movingAverages(prices: List<Double>): MovingAverageResult {
        val ma5 = if (prices.size >= 5) prices.take(5).average() else null
        val ma20 = if (prices.size >= 20) prices.take(20).average() else null
        val ma60 = if (prices.size >= 60) prices.take(60).average() else null

        return MovingAverageResult(ma5 = ma5, ma20 = ma20, ma60 = ma60)
    }

    // ============================
    // 내부 헬퍼
    // ============================

    private fun ema(prices: List<Double>, period: Int): Double? {
        if (prices.size < period) return null
        val k = 2.0 / (period + 1)
        var ema = prices.take(period).average()
        for (i in period until prices.size) {
            ema = prices[i] * k + ema * (1 - k)
        }
        return ema
    }

    private fun emaAt(prices: List<Double>, period: Int, offset: Int): Double? {
        if (prices.size < period + offset) return null
        val slice = prices.drop(offset)
        return ema(slice, period)
    }

    private fun emaList(values: List<Double>, period: Int): Double? {
        if (values.size < period) return null
        val k = 2.0 / (period + 1)
        var ema = values.take(period).average()
        for (i in period until values.size) {
            ema = values[i] * k + ema * (1 - k)
        }
        return ema
    }
}

// ============================
// 데이터 클래스 및 Enum
// ============================

data class MacdResult(
    val macd: Double,
    val signal: Double,
    val histogram: Double,
    val prevHistogram: Double?
)

enum class MacdSignal {
    GOLDEN_CROSS, BULLISH, WEAKLY_BULLISH, WEAKLY_BEARISH, BEARISH, DEAD_CROSS
}

data class BollingerResult(
    val upper: Double,
    val middle: Double,
    val lower: Double,
    val percentB: Double
)

enum class BollingerSignal {
    BELOW_LOWER, NEAR_LOWER, MIDDLE, NEAR_UPPER, ABOVE_UPPER
}

enum class RsiSignal {
    OVERSOLD, NEAR_OVERSOLD, NEUTRAL, NEAR_OVERBOUGHT, OVERBOUGHT
}

data class MovingAverageResult(
    val ma5: Double?,
    val ma20: Double?,
    val ma60: Double?
) {
    fun isUptrend(): Boolean {
        val m5 = ma5 ?: return false
        val m20 = ma20 ?: return false
        val m60 = ma60 ?: return false
        return m5 > m20 && m20 > m60
    }

    fun isDowntrend(): Boolean {
        val m5 = ma5 ?: return false
        val m20 = ma20 ?: return false
        val m60 = ma60 ?: return false
        return m5 < m20 && m20 < m60
    }
}
