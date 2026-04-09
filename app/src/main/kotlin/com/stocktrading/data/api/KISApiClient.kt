package com.stocktrading.data.api

import com.google.gson.GsonBuilder
import com.stocktrading.security.SecureCredentialManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KIS API Retrofit 클라이언트 팩토리
 * 실전투자와 모의투자 URL을 구분하여 클라이언트 생성
 */
@Singleton
class KISApiClient @Inject constructor(
    private val credentialManager: SecureCredentialManager,
    private val authInterceptor: KISAuthInterceptor,
    private val serviceProvider: KISApiServiceProvider
) {
    companion object {
        /** 실전투자 Base URL */
        const val REAL_BASE_URL = "https://openapi.koreainvestment.com:9443/"

        /** 모의투자 Base URL */
        const val MOCK_BASE_URL = "https://openapivts.koreainvestment.com:29443/"

        private const val CONNECT_TIMEOUT_SEC = 30L
        private const val READ_TIMEOUT_SEC = 30L
        private const val WRITE_TIMEOUT_SEC = 30L
    }

    private var _service: KISApiService? = null
    // 현재 서비스가 생성된 모드를 추적 (모드 변경 감지용)
    private var _currentIsMockMode: Boolean? = null

    /**
     * KISApiService 인스턴스 반환 (현재 설정된 환경에 따라 URL 결정)
     * 모의/실전 모드가 바뀌면 자동으로 URL이 다른 서비스로 재생성
     */
    fun getService(): KISApiService {
        val isMockMode = credentialManager.isMockMode()
        val baseUrl = if (isMockMode) MOCK_BASE_URL else REAL_BASE_URL

        // 서비스가 없거나 모드가 변경된 경우 재생성
        if (_service == null || _currentIsMockMode != isMockMode) {
            _service = createService(baseUrl)
            _currentIsMockMode = isMockMode
            serviceProvider.setService(_service!!)
        }

        return _service!!
    }

    /**
     * 환경 변경 시 서비스 재초기화
     */
    fun resetService() {
        _service = null
    }

    private fun createService(baseUrl: String): KISApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Debug 빌드에서만 전체 로그, Release에서는 기본만
            level = if (isDebugBuild()) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()

        val gson = GsonBuilder()
            .setLenient()
            .create()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(KISApiService::class.java)
    }

    private fun isDebugBuild(): Boolean {
        return try {
            val buildConfigClass = Class.forName("com.stocktrading.BuildConfig")
            buildConfigClass.getField("DEBUG").getBoolean(null)
        } catch (e: Exception) {
            false
        }
    }
}
