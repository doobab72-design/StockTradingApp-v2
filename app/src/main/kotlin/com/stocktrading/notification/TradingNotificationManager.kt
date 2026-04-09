package com.stocktrading.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.stocktrading.MainActivity
import com.stocktrading.R
import com.stocktrading.data.model.RecommendedStock
import com.stocktrading.data.model.TradingSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 거래 알림 관리자
 * 추천 종목 알림, 매매 신호 알림 등을 담당
 */
@Singleton
class TradingNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // 알림 채널 ID
        const val CHANNEL_RECOMMENDATION = "channel_recommendation"
        const val CHANNEL_TRADING_SIGNAL = "channel_trading_signal"
        const val CHANNEL_ORDER_STATUS = "channel_order_status"
        const val CHANNEL_SYSTEM = "channel_system"

        // 알림 ID 범위
        private const val NOTIFICATION_ID_RECOMMENDATION = 1000
        private const val NOTIFICATION_ID_SIGNAL_BASE = 2000
        private const val NOTIFICATION_ID_ORDER_BASE = 3000
        private const val NOTIFICATION_ID_SYSTEM = 9000
    }

    init {
        createNotificationChannels()
    }

    /**
     * 알림 채널 생성 (Android 8.0+)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 추천 종목 채널
            NotificationChannel(
                CHANNEL_RECOMMENDATION,
                "추천 종목",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "매일 오전 8시 추천 종목 알림"
                enableVibration(true)
            }.also { notificationManager.createNotificationChannel(it) }

            // 매매 신호 채널
            NotificationChannel(
                CHANNEL_TRADING_SIGNAL,
                "매매 신호",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "보유 종목 매매 신호 알림"
                enableVibration(true)
                enableLights(true)
            }.also { notificationManager.createNotificationChannel(it) }

            // 주문 상태 채널
            NotificationChannel(
                CHANNEL_ORDER_STATUS,
                "주문 상태",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "주문 체결 및 상태 변경 알림"
            }.also { notificationManager.createNotificationChannel(it) }

            // 시스템 알림 채널
            NotificationChannel(
                CHANNEL_SYSTEM,
                "시스템",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "앱 시스템 알림"
            }.also { notificationManager.createNotificationChannel(it) }
        }
    }

    /**
     * 추천 종목 알림 전송 (오전 8시)
     * @param recommendations 추천 종목 목록 (최대 3개)
     */
    fun sendRecommendationNotification(recommendations: List<RecommendedStock>) {
        if (recommendations.isEmpty()) return

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "recommendations")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_RECOMMENDATION,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "📈 오늘의 추천 종목 ${recommendations.size}개"
        val topStock = recommendations.first()
        val content = "${topStock.stockName} (${String.format("+%.1f%%", topStock.expectedReturnRate)} 목표)"

        val style = NotificationCompat.InboxStyle()
        recommendations.forEach { stock ->
            style.addLine(
                "• ${stock.stockName}: ${String.format("%,.0f", stock.currentPrice)}원 " +
                        "(목표 ${String.format("+%.1f%%", stock.expectedReturnRate)})"
            )
        }
        style.setBigContentTitle(title)
        style.setSummaryText("탭하여 상세 내용 확인")

        val notification = NotificationCompat.Builder(context, CHANNEL_RECOMMENDATION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_RECOMMENDATION, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS 권한 없음
        }
    }

    /**
     * 매매 신호 알림 전송
     * @param signal 매매 신호
     */
    fun sendTradingSignalNotification(signal: TradingSignal) {
        val signalText = when (signal.signalType.name) {
            "STRONG_BUY" -> "🟢 강력 매수"
            "BUY" -> "🟩 매수"
            "SELL" -> "🟥 매도"
            "STRONG_SELL" -> "🔴 강력 매도"
            else -> return  // HOLD는 알림 불필요
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "portfolio")
            putExtra("stock_code", signal.stockCode)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_SIGNAL_BASE + signal.stockCode.hashCode(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "$signalText 신호: ${signal.stockName}"
        val content = "${String.format("%,.0f", signal.recommendedPrice)}원 | " +
                "점수: ${String.format("%.0f", signal.score)} | " +
                signal.reasons.firstOrNull()

        val notification = NotificationCompat.Builder(context, CHANNEL_TRADING_SIGNAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(signal.reasons.joinToString("\n• ", prefix = "• ")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_SIGNAL_BASE + signal.stockCode.hashCode(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS 권한 없음
        }
    }

    /**
     * 주문 체결 알림
     */
    fun sendOrderExecutedNotification(stockName: String, tradeType: String, quantity: Int, price: Double) {
        val title = "${if (tradeType == "BUY") "✅ 매수" else "📤 매도"} 주문 전송 완료"
        val content = "$stockName | ${quantity}주 @ ${String.format("%,.0f", price)}원"

        val notification = NotificationCompat.Builder(context, CHANNEL_ORDER_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_ORDER_BASE + System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            // 권한 없음
        }
    }

    /**
     * API 키 미설정 알림
     */
    fun sendSetupRequiredNotification() {
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "settings")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚙️ 초기 설정 필요")
            .setContentText("KIS API 키와 계좌 정보를 설정해주세요.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_SYSTEM, notification)
        } catch (e: SecurityException) {
            // 권한 없음
        }
    }
}
