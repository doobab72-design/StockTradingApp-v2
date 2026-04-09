package com.stocktrading.analysis

import com.stocktrading.data.model.PriceData
import java.util.Calendar

/**
 * 주간 패턴 분석기
 * 요일별 수익률 패턴, 주간 트렌드, 거래량 패턴 분석
 */
class WeeklyPatternAnalyzer {

    /**
     * 주간 패턴 분석 결과
     * @param priceDataList 주가 데이터 (최신 → 오래된 순), 최소 20일 권장
     */
    fun analyze(priceDataList: List<PriceData>): WeeklyPatternResult {
        if (priceDataList.size < 10) {
            return WeeklyPatternResult.empty()
        }

        val dayOfWeekReturns = analyzeDayOfWeekReturns(priceDataList)
        val weeklyTrend = analyzeWeeklyTrend(priceDataList)
        val volumePattern = analyzeVolumePattern(priceDataList)
        val consecutiveDays = analyzeConsecutiveDays(priceDataList)

        return WeeklyPatternResult(
            dayOfWeekReturns = dayOfWeekReturns,
            weeklyTrend = weeklyTrend,
            volumePattern = volumePattern,
            consecutiveUpDays = consecutiveDays.first,
            consecutiveDownDays = consecutiveDays.second,
            isMondayEffect = checkMondayEffect(dayOfWeekReturns),
            score = calculatePatternScore(dayOfWeekReturns, weeklyTrend, consecutiveDays)
        )
    }

    /**
     * 요일별 평균 수익률 계산
     */
    private fun analyzeDayOfWeekReturns(priceDataList: List<PriceData>): Map<Int, Double> {
        val returnsByDay = mutableMapOf<Int, MutableList<Double>>()

        for (i in 0 until priceDataList.size - 1) {
            val current = priceDataList[i]
            val previous = priceDataList[i + 1]
            val returnRate = if (previous.closePrice > 0) {
                (current.closePrice - previous.closePrice) / previous.closePrice * 100
            } else continue

            val dayOfWeek = getDayOfWeek(current.date)
            returnsByDay.getOrPut(dayOfWeek) { mutableListOf() }.add(returnRate)
        }

        return returnsByDay.mapValues { (_, returns) -> returns.average() }
    }

    /**
     * 주간 트렌드 분석 (최근 5주)
     */
    private fun analyzeWeeklyTrend(priceDataList: List<PriceData>): TrendDirection {
        if (priceDataList.size < 5) return TrendDirection.NEUTRAL

        // 최근 5일 vs 이전 5일 비교
        val recent5 = priceDataList.take(5).map { it.closePrice }.average()
        val prev5 = priceDataList.drop(5).take(5).map { it.closePrice }.average()

        val changeRate = if (prev5 > 0) (recent5 - prev5) / prev5 * 100 else 0.0

        return when {
            changeRate > 3.0 -> TrendDirection.STRONG_UP
            changeRate > 1.0 -> TrendDirection.UP
            changeRate < -3.0 -> TrendDirection.STRONG_DOWN
            changeRate < -1.0 -> TrendDirection.DOWN
            else -> TrendDirection.NEUTRAL
        }
    }

    /**
     * 거래량 패턴 분석 (평균 대비 현재 거래량)
     */
    private fun analyzeVolumePattern(priceDataList: List<PriceData>): VolumePattern {
        if (priceDataList.isEmpty()) return VolumePattern.NORMAL

        val avgVolume = priceDataList.drop(1).take(19).map { it.volume.toDouble() }.average()
        val currentVolume = priceDataList.first().volume.toDouble()

        val ratio = if (avgVolume > 0) currentVolume / avgVolume else 1.0

        return when {
            ratio > 2.0 -> VolumePattern.HIGH_VOLUME   // 거래량 급증
            ratio > 1.5 -> VolumePattern.ABOVE_AVERAGE
            ratio < 0.5 -> VolumePattern.LOW_VOLUME    // 거래량 급감
            ratio < 0.8 -> VolumePattern.BELOW_AVERAGE
            else -> VolumePattern.NORMAL
        }
    }

    /**
     * 연속 상승/하락 일수 계산
     */
    private fun analyzeConsecutiveDays(priceDataList: List<PriceData>): Pair<Int, Int> {
        var upDays = 0
        var downDays = 0

        for (i in 0 until priceDataList.size - 1) {
            val current = priceDataList[i].closePrice
            val previous = priceDataList[i + 1].closePrice

            if (current > previous) {
                if (downDays == 0) upDays++
                else break
            } else if (current < previous) {
                if (upDays == 0) downDays++
                else break
            } else {
                break
            }
        }

        return Pair(upDays, downDays)
    }

    /**
     * 월요일 효과 체크 (월요일 평균 수익률이 낮은 패턴)
     */
    private fun checkMondayEffect(dayOfWeekReturns: Map<Int, Double>): Boolean {
        val mondayReturn = dayOfWeekReturns[Calendar.MONDAY] ?: return false
        val weekAvg = dayOfWeekReturns.values.average()
        return mondayReturn < weekAvg - 0.5
    }

    /**
     * 패턴 종합 점수 계산 (-30 ~ +30)
     */
    private fun calculatePatternScore(
        dayOfWeekReturns: Map<Int, Double>,
        weeklyTrend: TrendDirection,
        consecutiveDays: Pair<Int, Int>
    ): Double {
        var score = 0.0

        // 주간 트렌드 점수
        score += when (weeklyTrend) {
            TrendDirection.STRONG_UP -> 15.0
            TrendDirection.UP -> 8.0
            TrendDirection.NEUTRAL -> 0.0
            TrendDirection.DOWN -> -8.0
            TrendDirection.STRONG_DOWN -> -15.0
        }

        // 연속 상승일 (3일 이상 연속 상승은 조정 가능성)
        val upDays = consecutiveDays.first
        val downDays = consecutiveDays.second
        score += when {
            upDays in 1..2 -> 5.0     // 단기 상승 모멘텀
            upDays >= 3 -> -3.0       // 과열 주의
            downDays in 1..2 -> -5.0  // 단기 하락
            downDays >= 3 -> 5.0      // 반등 가능성
            else -> 0.0
        }

        return score.coerceIn(-30.0, 30.0)
    }

    /**
     * 날짜 문자열(yyyyMMdd)에서 요일 추출
     */
    private fun getDayOfWeek(dateStr: String): Int {
        return try {
            val year = dateStr.substring(0, 4).toInt()
            val month = dateStr.substring(4, 6).toInt() - 1
            val day = dateStr.substring(6, 8).toInt()
            Calendar.getInstance().apply { set(year, month, day) }.get(Calendar.DAY_OF_WEEK)
        } catch (e: Exception) {
            Calendar.MONDAY
        }
    }
}

data class WeeklyPatternResult(
    val dayOfWeekReturns: Map<Int, Double>,
    val weeklyTrend: TrendDirection,
    val volumePattern: VolumePattern,
    val consecutiveUpDays: Int,
    val consecutiveDownDays: Int,
    val isMondayEffect: Boolean,
    val score: Double
) {
    companion object {
        fun empty() = WeeklyPatternResult(
            dayOfWeekReturns = emptyMap(),
            weeklyTrend = TrendDirection.NEUTRAL,
            volumePattern = VolumePattern.NORMAL,
            consecutiveUpDays = 0,
            consecutiveDownDays = 0,
            isMondayEffect = false,
            score = 0.0
        )
    }
}

enum class TrendDirection {
    STRONG_UP, UP, NEUTRAL, DOWN, STRONG_DOWN
}

enum class VolumePattern {
    HIGH_VOLUME, ABOVE_AVERAGE, NORMAL, BELOW_AVERAGE, LOW_VOLUME
}
