package com.stocktrading.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.stocktrading.security.SecureCredentialManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Firebase Cloud Messaging 서비스
 * FCM 토큰 갱신 및 원격 푸시 알림 수신 처리
 */
@AndroidEntryPoint
class StockFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "StockFCMService"
    }

    @Inject
    lateinit var credentialManager: SecureCredentialManager

    @Inject
    lateinit var notificationManager: TradingNotificationManager

    /**
     * FCM 토큰 갱신 시 호출
     * 서버 연동이 있다면 여기서 새 토큰을 서버에 전송
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "새 FCM 토큰 발급: ${token.take(20)}...")
        credentialManager.saveFcmToken(token)
        // TODO: 서버에 토큰 전송 (서버 연동 시)
    }

    /**
     * FCM 메시지 수신 처리
     * 원격 서버에서 보내는 신호/알림 처리
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM 메시지 수신: ${message.messageId}")

        val data = message.data
        val notification = message.notification

        when (data["type"]) {
            "trading_signal" -> {
                // 서버에서 보내는 매매 신호 (향후 확장)
                Log.i(TAG, "원격 매매 신호 수신: ${data["stock_code"]}")
            }
            "recommendation" -> {
                // 서버에서 보내는 추천 알림
                Log.i(TAG, "원격 추천 알림 수신")
            }
            else -> {
                // 일반 알림 메시지
                notification?.let {
                    Log.d(TAG, "일반 알림: ${it.title} - ${it.body}")
                }
            }
        }
    }
}
