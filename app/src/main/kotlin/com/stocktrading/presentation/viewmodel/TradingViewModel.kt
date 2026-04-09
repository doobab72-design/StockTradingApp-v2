package com.stocktrading.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocktrading.data.repository.TradingRepository
import com.stocktrading.notification.TradingNotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 거래 실행 ViewModel
 * 주문 확인, 실행, 취소 처리
 */
@HiltViewModel
class TradingViewModel @Inject constructor(
    private val tradingRepository: TradingRepository,
    private val notificationManager: TradingNotificationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TradingUiState())
    val uiState: StateFlow<TradingUiState> = _uiState.asStateFlow()

    /**
     * 주문 실행 (사용자가 "확인" 버튼 클릭 후 호출)
     * @param recordId 거래 내역 ID
     */
    fun executeOrder(recordId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true, executingOrderId = recordId) }

            tradingRepository.executeOrder(recordId)
                .onSuccess { orderResponse ->
                    _uiState.update {
                        it.copy(
                            isExecuting = false,
                            executingOrderId = null,
                            successMessage = "주문이 전송되었습니다."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isExecuting = false,
                            executingOrderId = null,
                            errorMessage = "주문 실패: ${error.message}"
                        )
                    }
                }
        }
    }

    /**
     * 주문 취소 (사용자가 "취소" 버튼 클릭 후 호출)
     */
    fun cancelOrder(recordId: Long) {
        viewModelScope.launch {
            tradingRepository.cancelOrder(recordId)
            _uiState.update { it.copy(successMessage = "주문이 취소되었습니다.") }
        }
    }

    /**
     * 전체 PENDING 주문 취소
     */
    fun cancelAllPendingOrders() {
        viewModelScope.launch {
            val pendingOrders = tradingRepository.getPendingOrders()
            pendingOrders.forEach { order ->
                tradingRepository.cancelOrder(order.id)
            }
            _uiState.update { it.copy(successMessage = "${pendingOrders.size}개 주문 취소 완료") }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}

data class TradingUiState(
    val isExecuting: Boolean = false,
    val executingOrderId: Long? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null
)
