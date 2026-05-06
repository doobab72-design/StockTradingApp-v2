package com.stocktrading.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocktrading.data.api.KISApiClient
import com.stocktrading.data.api.KISTokenManager
import com.stocktrading.security.SecureCredentialManager
import com.stocktrading.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialManager: SecureCredentialManager,
    private val kisApiClient: KISApiClient,
    private val tokenManager: KISTokenManager,
    private val workScheduler: WorkScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCurrentSettings()
    }

    private fun loadCurrentSettings() {
        _uiState.update {
            it.copy(
                appKey = credentialManager.getAppKey() ?: "",
                appSecret = credentialManager.getAppSecret() ?: "",
                accountNo = credentialManager.getAccountNo() ?: "",
                accountProductCode = credentialManager.getAccountProductCode() ?: "01",
                isMockMode = credentialManager.isMockMode()
            )
        }
    }

    fun updateAppKey(key: String) = _uiState.update { it.copy(appKey = key) }
    fun updateAppSecret(secret: String) = _uiState.update { it.copy(appSecret = secret) }
    fun updateAccountNo(no: String) = _uiState.update { it.copy(accountNo = no) }
    fun updateAccountProductCode(code: String) = _uiState.update { it.copy(accountProductCode = code) }

    fun setMockMode(isMock: Boolean) {
        credentialManager.setMockMode(isMock)
        kisApiClient.resetService()  // 서비스 재초기화 (URL 변경)
        tokenManager.clearToken()    // 기존 토큰 폐기
        _uiState.update { it.copy(isMockMode = isMock) }
        _uiState.update { it.copy(message = "거래 모드가 ${if (isMock) "모의투자" else "실전투자"}로 변경되었습니다.") }
    }

    fun saveApiKeys() {
        val appKey = uiState.value.appKey.trim()
        val appSecret = uiState.value.appSecret.trim()

        if (appKey.isBlank() || appSecret.isBlank()) {
            _uiState.update { it.copy(message = "앱 키와 앱 시크릿을 모두 입력해주세요.") }
            return
        }

        credentialManager.saveAppKey(appKey)
        credentialManager.saveAppSecret(appSecret)
        tokenManager.clearToken()   // 키 변경 시 기존 토큰 초기화
        kisApiClient.resetService()

        _uiState.update { it.copy(message = "API 키가 저장되었습니다.") }
    }

    fun saveAccountInfo() {
        val accountNo = uiState.value.accountNo.trim()
        val productCode = uiState.value.accountProductCode.trim()

        if (accountNo.isBlank()) {
            _uiState.update { it.copy(message = "계좌번호를 입력해주세요.") }
            return
        }

        credentialManager.saveAccountNo(accountNo)
        credentialManager.saveAccountProductCode(productCode.ifBlank { "01" })
        _uiState.update { it.copy(message = "계좌 정보가 저장되었습니다.") }
    }

    fun triggerDailyRecommendation() {
        workScheduler.triggerDailyRecommendationNow()
        _uiState.update { it.copy(message = "추천 분석 Worker를 실행했습니다.") }
    }

    fun triggerAutoTrading() {
        workScheduler.triggerAutoTradingNow()
        _uiState.update { it.copy(message = "매매 분석 Worker를 실행했습니다.") }
    }

    fun clearAllCredentials() {
        credentialManager.clearAll()
        tokenManager.clearToken()
        kisApiClient.resetService()
        workScheduler.cancelAll()
        loadCurrentSettings()
        _uiState.update { it.copy(message = "모든 설정이 초기화되었습니다.") }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

data class SettingsUiState(
    val appKey: String = "",
    val appSecret: String = "",
    val accountNo: String = "",
    val accountProductCode: String = "01",
    val isMockMode: Boolean = true,
    val message: String? = null
)
