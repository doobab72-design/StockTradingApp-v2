package com.stocktrading.data.api

import android.util.Log
import com.stocktrading.security.SecureCredentialManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KIS API 인증 인터셉터
 * 모든 요청에 자동으로 Authorization 헤더와 앱 키를 추가
 * 토큰 만료 시 자동 갱신 처리
 */
@Singleton
class KISAuthInterceptor @Inject constructor(
    private val credentialManager: SecureCredentialManager,
    private val tokenManager: KISTokenManager
) : Interceptor {

    companion object {
        private const val TAG = "KISAuthInterceptor"
        private const val MAX_RETRY = 1
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // OAuth2 토큰 발급 요청은 인터셉터 적용 제외
        if (originalRequest.url.encodedPath.contains("oauth2")) {
            return chain.proceed(originalRequest)
        }

        val appKey = credentialManager.getAppKey()
        val appSecret = credentialManager.getAppSecret()

        if (appKey.isNullOrBlank() || appSecret.isNullOrBlank()) {
            Log.w(TAG, "API 키가 설정되지 않았습니다.")
            return chain.proceed(originalRequest)
        }

        // 현재 유효한 토큰 가져오기 (없으면 새로 발급)
        val accessToken = runBlocking {
            tokenManager.getValidToken(appKey, appSecret)
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .header("authorization", "Bearer $accessToken")
            .header("appkey", appKey)
            .header("appsecret", appSecret)
            .header("content-type", "application/json; charset=utf-8")
            .build()

        val response = chain.proceed(authenticatedRequest)

        // 401 응답 시 토큰 갱신 후 재시도
        if (response.code == 401) {
            response.close()
            Log.i(TAG, "토큰 만료 감지 - 토큰 갱신 시도")

            val newToken = runBlocking {
                tokenManager.refreshToken(appKey, appSecret)
            }

            val retryRequest = originalRequest.newBuilder()
                .header("authorization", "Bearer $newToken")
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("content-type", "application/json; charset=utf-8")
                .build()

            return chain.proceed(retryRequest)
        }

        return response
    }
}
