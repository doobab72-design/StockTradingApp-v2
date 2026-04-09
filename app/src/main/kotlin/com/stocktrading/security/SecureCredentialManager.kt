package com.stocktrading.security

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 보안 자격증명 관리자
 * EncryptedSharedPreferences를 사용하여 API 키, 계좌 정보 등 민감 정보를 암호화 저장
 */
@Singleton
class SecureCredentialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SecureCredentialManager"
        private const val PREFS_FILE = "secure_credentials"

        // 저장 키 상수
        private const val KEY_APP_KEY = "kis_app_key"
        private const val KEY_APP_SECRET = "kis_app_secret"
        private const val KEY_ACCESS_TOKEN = "kis_access_token"
        private const val KEY_ACCOUNT_NO = "kis_account_no"
        private const val KEY_ACCOUNT_PRODUCT_CODE = "kis_account_product_code"
        private const val KEY_IS_MOCK_MODE = "is_mock_mode"
        private const val KEY_FCM_TOKEN = "fcm_token"
    }

    private val encryptedPrefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences 초기화 실패", e)
            // 폴백: 일반 SharedPreferences (개발 환경에서만)
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    // ============================
    // KIS API 키
    // ============================

    fun saveAppKey(appKey: String) {
        encryptedPrefs.edit().putString(KEY_APP_KEY, appKey).apply()
        Log.d(TAG, "앱 키 저장 완료")
    }

    fun getAppKey(): String? = encryptedPrefs.getString(KEY_APP_KEY, null)

    fun saveAppSecret(appSecret: String) {
        encryptedPrefs.edit().putString(KEY_APP_SECRET, appSecret).apply()
        Log.d(TAG, "앱 시크릿 저장 완료")
    }

    fun getAppSecret(): String? = encryptedPrefs.getString(KEY_APP_SECRET, null)

    // ============================
    // 액세스 토큰
    // ============================

    fun saveAccessToken(token: String) {
        encryptedPrefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun getAccessToken(): String? = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)

    fun clearAccessToken() {
        encryptedPrefs.edit().remove(KEY_ACCESS_TOKEN).apply()
    }

    // ============================
    // 계좌 정보
    // ============================

    fun saveAccountNo(accountNo: String) {
        encryptedPrefs.edit().putString(KEY_ACCOUNT_NO, accountNo).apply()
    }

    fun getAccountNo(): String? = encryptedPrefs.getString(KEY_ACCOUNT_NO, null)

    fun saveAccountProductCode(code: String) {
        encryptedPrefs.edit().putString(KEY_ACCOUNT_PRODUCT_CODE, code).apply()
    }

    fun getAccountProductCode(): String? =
        encryptedPrefs.getString(KEY_ACCOUNT_PRODUCT_CODE, "01")

    // ============================
    // 모의투자 모드
    // ============================

    fun setMockMode(isMock: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_IS_MOCK_MODE, isMock).apply()
        Log.i(TAG, "거래 모드 변경: ${if (isMock) "모의투자" else "실전투자"}")
    }

    fun isMockMode(): Boolean = encryptedPrefs.getBoolean(KEY_IS_MOCK_MODE, true)  // 기본: 모의투자

    // ============================
    // FCM 토큰
    // ============================

    fun saveFcmToken(token: String) {
        encryptedPrefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getFcmToken(): String? = encryptedPrefs.getString(KEY_FCM_TOKEN, null)

    // ============================
    // 유효성 검사
    // ============================

    /**
     * API 키 설정 완료 여부 확인
     */
    fun isApiKeyConfigured(): Boolean {
        val appKey = getAppKey()
        val appSecret = getAppSecret()
        return !appKey.isNullOrBlank() && !appSecret.isNullOrBlank() &&
                appKey != "YOUR_APP_KEY_HERE" && appSecret != "YOUR_APP_SECRET_HERE"
    }

    /**
     * 계좌 정보 설정 완료 여부 확인
     */
    fun isAccountConfigured(): Boolean {
        val accountNo = getAccountNo()
        return !accountNo.isNullOrBlank() && accountNo != "YOUR_ACCOUNT_NO_HERE"
    }

    /**
     * 전체 설정 완료 여부
     */
    fun isFullyConfigured(): Boolean = isApiKeyConfigured() && isAccountConfigured()

    /**
     * 모든 자격증명 초기화 (로그아웃)
     */
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
        Log.i(TAG, "모든 자격증명 초기화 완료")
    }
}
