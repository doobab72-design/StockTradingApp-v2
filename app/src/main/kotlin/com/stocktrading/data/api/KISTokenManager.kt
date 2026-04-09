package com.stocktrading.data.api

import android.util.Log
import com.stocktrading.security.SecureCredentialManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KIS API 접근 토큰 관리자
 * 토큰 발급, 갱신, 만료 체크를 담당
 * Mutex로 동시 요청 시 중복 발급 방지
 */
@Singleton
class KISTokenManager @Inject constructor(
    private val credentialManager: SecureCredentialManager,
    private val apiServiceProvider: KISApiServiceProvider
) {
    companion object {
        private const val TAG = "KISTokenManager"
        // 만료 5분 전에 미리 갱신
        private const val TOKEN_EXPIRY_BUFFER_MS = 5 * 60 * 1000L
    }

    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiryTime: Long = 0L

    /**
     * 유효한 토큰 반환 (없거나 만료 시 자동 발급)
     */
    suspend fun getValidToken(appKey: String, appSecret: String): String {
        // 캐시된 토큰이 유효한 경우 그대로 반환
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryTime - TOKEN_EXPIRY_BUFFER_MS) {
            return cachedToken!!
        }

        return mutex.withLock {
            // Double-checked locking
            val nowInLock = System.currentTimeMillis()
            if (cachedToken != null && nowInLock < tokenExpiryTime - TOKEN_EXPIRY_BUFFER_MS) {
                return@withLock cachedToken!!
            }
            issueNewToken(appKey, appSecret)
        }
    }

    /**
     * 토큰 강제 갱신
     */
    suspend fun refreshToken(appKey: String, appSecret: String): String {
        return mutex.withLock {
            cachedToken = null
            issueNewToken(appKey, appSecret)
        }
    }

    private suspend fun issueNewToken(appKey: String, appSecret: String): String {
        Log.i(TAG, "새 액세스 토큰 발급 요청")

        val response = apiServiceProvider.getService()
            .getAccessToken(TokenRequest(appkey = appKey, appsecret = appSecret))

        return if (response.isSuccessful) {
            val tokenResponse = response.body()
                ?: throw IllegalStateException("토큰 응답 본문이 비어있습니다.")

            cachedToken = tokenResponse.access_token
            // expires_in은 초 단위
            tokenExpiryTime = System.currentTimeMillis() + (tokenResponse.expires_in * 1000L)

            // 보안 저장소에 토큰 저장
            credentialManager.saveAccessToken(tokenResponse.access_token)

            Log.i(TAG, "토큰 발급 성공 (만료: ${tokenResponse.access_token_token_expired})")
            tokenResponse.access_token
        } else {
            val errorMsg = "토큰 발급 실패: ${response.code()} ${response.message()}"
            Log.e(TAG, errorMsg)
            throw IllegalStateException(errorMsg)
        }
    }

    fun clearToken() {
        cachedToken = null
        tokenExpiryTime = 0L
        credentialManager.clearAccessToken()
    }
}

/**
 * 순환 의존성 방지를 위한 KISApiService 지연 제공자
 */
@Singleton
class KISApiServiceProvider @Inject constructor() {
    private var service: KISApiService? = null

    fun setService(kisApiService: KISApiService) {
        service = kisApiService
    }

    fun getService(): KISApiService {
        return service ?: throw IllegalStateException("KISApiService가 초기화되지 않았습니다.")
    }
}
