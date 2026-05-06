package com.stocktrading.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stocktrading.data.model.Portfolio
import com.stocktrading.data.model.TradingRecord
import com.stocktrading.data.model.TradeType
import com.stocktrading.presentation.viewmodel.DashboardViewModel
import com.stocktrading.presentation.viewmodel.TradingViewModel

/**
 * 보유 종목 및 대기 주문 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onBack: () -> Unit = {},
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    tradingViewModel: TradingViewModel = hiltViewModel()
) {
    val portfolio by dashboardViewModel.portfolio.collectAsStateWithLifecycle()
    val pendingOrders by dashboardViewModel.pendingOrders.collectAsStateWithLifecycle()
    val tradingUiState by tradingViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(tradingUiState.successMessage) {
        tradingUiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            tradingViewModel.clearMessages()
        }
    }
    LaunchedEffect(tradingUiState.errorMessage) {
        tradingUiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            tradingViewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("보유 종목") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 대기 중인 주문 섹션
            if (pendingOrders.isNotEmpty()) {
                item {
                    Text(
                        text = "확인 대기 중인 주문 (${pendingOrders.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                items(pendingOrders, key = { it.id }) { order ->
                    PendingOrderCard(
                        order = order,
                        isExecuting = tradingUiState.executingOrderId == order.id,
                        onExecute = { tradingViewModel.executeOrder(order.id) },
                        onCancel = { tradingViewModel.cancelOrder(order.id) }
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            }

            // 보유 종목 섹션
            item {
                Text(
                    text = "보유 종목 (${portfolio.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (portfolio.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("보유 종목이 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(portfolio, key = { it.stockCode }) { item ->
                    PortfolioDetailCard(portfolio = item)
                }
            }
        }
    }
}

@Composable
private fun PendingOrderCard(
    order: TradingRecord,
    isExecuting: Boolean,
    onExecute: () -> Unit,
    onCancel: () -> Unit
) {
    val isBuy = order.tradeType == TradeType.BUY
    val typeColor = if (isBuy) Color(0xFFE53935) else Color(0xFF1565C0)
    val typeLabel = if (isBuy) "매수" else "매도"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, typeColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = typeColor.copy(alpha = 0.1f), shape = MaterialTheme.shapes.small) {
                        Text(
                            text = typeLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = typeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(order.stockName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = "${String.format("%,.0f", order.totalAmount)}원",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "${order.quantity}주 × ${String.format("%,.0f", order.price)}원 (시장가)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (order.signalReason.isNotBlank()) {
                Text(
                    text = "신호 근거: ${order.signalReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !isExecuting
                ) {
                    Text("취소")
                }
                Button(
                    onClick = onExecute,
                    modifier = Modifier.weight(1f),
                    enabled = !isExecuting,
                    colors = ButtonDefaults.buttonColors(containerColor = typeColor)
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("${typeLabel} 주문 실행")
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioDetailCard(portfolio: Portfolio) {
    val profitColor = when {
        portfolio.profitLoss > 0 -> Color(0xFFE53935)
        portfolio.profitLoss < 0 -> Color(0xFF1565C0)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(portfolio.stockName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "${if (portfolio.profitRate >= 0) "+" else ""}${String.format("%.2f", portfolio.profitRate)}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = profitColor
                )
            }

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailItem("보유수량", "${portfolio.quantity}주")
                DetailItem("평균단가", "${String.format("%,.0f", portfolio.averageBuyPrice)}원")
                DetailItem("현재가", "${String.format("%,.0f", portfolio.currentPrice)}원")
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailItem("매입금액", "${String.format("%,.0f", portfolio.totalBuyAmount)}원")
                DetailItem("평가금액", "${String.format("%,.0f", portfolio.evaluationAmount)}원")
                DetailItem(
                    label = "평가손익",
                    value = "${if (portfolio.profitLoss >= 0) "+" else ""}${String.format("%,.0f", portfolio.profitLoss)}원",
                    valueColor = profitColor
                )
            }

            // 손절/익절 기준가
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = Color(0xFF1565C0).copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "손절 ${String.format("%,.0f", portfolio.stopLossPrice)}원 (-5%)",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1565C0)
                    )
                }
                Surface(
                    color = Color(0xFFE53935).copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "익절 ${String.format("%,.0f", portfolio.takeProfitPrice)}원 (+10%)",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE53935)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
