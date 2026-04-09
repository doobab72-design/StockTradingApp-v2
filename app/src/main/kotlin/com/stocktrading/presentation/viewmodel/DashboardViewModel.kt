package com.stocktrading.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocktrading.data.model.Portfolio
import com.stocktrading.data.model.RecommendedStock
import com.stocktrading.data.model.TradingRecord
import com.stocktrading.data.repository.StockRepository
import com.stocktrading.data.repository.TradingRepository
import com.stocktrading.security.SecureCredentialManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 대시보드 ViewModel
 * 추천 종목, 보유 종목, 거래 내역 데이터를 UI에 제공
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val stockRepository: StockRepository,
    private val tradingRepository: TradingRepository,
    private val credentialManager: SecureCredentialManager
) : ViewModel() {

    companion object {
        private const val TAG = "DashboardViewModel"
    }

    // ============================
    // UI State
    // ============================

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // 코루틴 전역 예외 핸들러 — 미처리 예외가 앱을 크래시시키지 않도록 방어
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "코루틴 미처리 예외", throwable)
        _uiState.update {
            it.copy(
                isRefreshing = false,
                isLoading = false,
                errorMessage = "오류 발생: ${throwable.message}"
            )
        }
    }

    // ============================
    // 실시간 데이터 스트림
    // ============================

    /** 보유 종목 Flow */
    val portfolio: StateFlow<List<Portfolio>> = stockRepository.observePortfolio()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 전체 거래 내역 Flow */
    val tradingHistory: StateFlow<List<TradingRecord>> = tradingRepository.observeAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 대기 중인 주문 Flow */
    val pendingOrders: StateFlow<List<TradingRecord>> = tradingRepository.observePendingOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInitialData()
        observePortfolioSummary()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val isConfigured = credentialManager.isFullyConfigured()
            val isMockMode = credentialManager.isMockMode()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isApiConfigured = isConfigured,
                    isMockMode = isMockMode
                )
            }
        }
    }

    private fun observePortfolioSummary() {
        viewModelScope.launch {
            portfolio.collect { portfolioList ->
                if (portfolioList.isNotEmpty()) {
                    val totalBuyAmount = portfolioList.sumOf { it.totalBuyAmount }
                    val totalEvalAmount = portfolioList.sumOf { it.evaluationAmount }
                    val totalProfitLoss = totalEvalAmount - totalBuyAmount
                    val totalProfitRate = if (totalBuyAmount > 0) {
                        (totalProfitLoss / totalBuyAmount) * 100
                    } else 0.0

                    _uiState.update {
                        it.copy(
                            totalBuyAmount = totalBuyAmount,
                            totalEvaluationAmount = totalEvalAmount,
                            totalProfitLoss = totalProfitLoss,
                            totalProfitRate = totalProfitRate
                        )
                    }
                }
            }
        }
    }

    /**
     * 포트폴리오 수동 새로고침
     * exceptionHandler로 코루틴 내 모든 미처리 예외를 잡아 크래시 방지
     */
    fun refreshPortfolio() {
        // 이미 리프레시 중이면 중복 호출 무시
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch(exceptionHandler) {
            val accountNo = credentialManager.getAccountNo()
            if (accountNo.isNullOrBlank()) {
                _uiState.update {
                    it.copy(errorMessage = "계좌번호가 설정되지 않았습니다. 설정 화면에서 입력해주세요.")
                }
                return@launch
            }

            val accountProductCode = credentialManager.getAccountProductCode() ?: "01"
            val isMockMode = credentialManager.isMockMode()

            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }

            try {
                stockRepository.syncPortfolioFromApi(accountNo, accountProductCode, isMockMode)
                    .onSuccess { portfolios ->
                        Log.i(TAG, "포트폴리오 리프레시 성공: ${portfolios.size}개")
                        _uiState.update { it.copy(isRefreshing = false) }
                    }
                    .onFailure { error ->
                        Log.e(TAG, "포트폴리오 리프레시 실패", error)
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                errorMessage = "포트폴리오 업데이트 실패: ${error.message}"
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "포트폴리오 리프레시 예외", e)
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = "오류: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 추천 종목 업데이트
     */
    fun updateRecommendations(recommendations: List<RecommendedStock>) {
        _uiState.update { it.copy(recommendations = recommendations) }
    }

    /**
     * 오류 메시지 초기화
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * 대시보드 UI 상태 데이터 클래스
 */
data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isApiConfigured: Boolean = false,
    val isMockMode: Boolean = true,
    val recommendations: List<RecommendedStock> = emptyList(),
    val totalBuyAmount: Double = 0.0,
    val totalEvaluationAmount: Double = 0.0,
    val totalProfitLoss: Double = 0.0,
    val totalProfitRate: Double = 0.0,
    val errorMessage: String? = null
)
