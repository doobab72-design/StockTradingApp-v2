package com.stocktrading.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.stocktrading.data.model.RecommendedStock
import com.stocktrading.data.model.TradingRecord
import com.stocktrading.presentation.viewmodel.DashboardViewModel

/**
 * 메인 대시보드 화면
 * 포트폴리오 요약, 보유 종목, 최근 거래 내역 표시
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToPortfolio: () -> Unit = {},
    onNavigateToRecommendations: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val portfolio by viewModel.portfolio.collectAsStateWithLifecycle()
    val tradingHistory by viewModel.tradingHistory.collectAsStateWithLifecycle()
    val pendingOrders by viewModel.pendingOrders.collectAsStateWithLifecycle()

    // 오류 메시지 스낵바
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("주식 자동매매") },
                actions = {
                    // 모의/실전 투자 뱃지
                    Badge(
                        containerColor = if (uiState.isMockMode) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.error
                    ) {
                        Text(
                            text = if (uiState.isMockMode) "모의" else "실전",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // API 미설정 경고
        if (!uiState.isApiConfigured) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                ApiNotConfiguredCard(onNavigateToSettings = onNavigateToSettings)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 포트폴리오 요약 카드
            item {
                PortfolioSummaryCard(
                    totalBuyAmount = uiState.totalBuyAmount,
                    totalEvaluationAmount = uiState.totalEvaluationAmount,
                    totalProfitLoss = uiState.totalProfitLoss,
                    totalProfitRate = uiState.totalProfitRate,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refreshPortfolio
                )
            }

            // 대기 중인 주문 (있을 경우 강조)
            if (pendingOrders.isNotEmpty()) {
                item {
                    PendingOrdersBanner(
                        count = pendingOrders.size,
                        onClick = onNavigateToPortfolio
                    )
                }
            }

            // 보유 종목 섹션
            item {
                SectionHeader(
                    title = "보유 종목 (${portfolio.size})",
                    actionLabel = "전체보기",
                    onAction = onNavigateToPortfolio
                )
            }

            if (portfolio.isEmpty()) {
                item {
                    EmptyStateCard(message = "보유 종목이 없습니다")
                }
            } else {
                items(portfolio.take(3)) { item ->
                    PortfolioItemCard(portfolio = item)
                }
            }

            // 추천 종목 섹션
            item {
                SectionHeader(
                    title = "오늘의 추천 종목",
                    actionLabel = "전체보기",
                    onAction = onNavigateToRecommendations
                )
            }

            if (uiState.recommendations.isEmpty()) {
                item {
                    EmptyStateCard(message = "추천 종목은 매일 오전 8시에 업데이트됩니다")
                }
            } else {
                items(uiState.recommendations) { stock ->
                    RecommendedStockCard(stock = stock)
                }
            }

            // 최근 거래 내역
            item {
                SectionHeader(title = "최근 거래 내역", actionLabel = null)
            }

            if (tradingHistory.isEmpty()) {
                item {
                    EmptyStateCard(message = "거래 내역이 없습니다")
                }
            } else {
                items(tradingHistory.take(5)) { record ->
                    TradingRecordCard(record = record)
                }
            }
        }
    }
}

@Composable
private fun PortfolioSummaryCard(
    totalBuyAmount: Double,
    totalEvaluationAmount: Double,
    totalProfitLoss: Double,
    totalProfitRate: Double,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val profitColor = when {
        totalProfitLoss > 0 -> Color(0xFFE53935)  // 상승: 빨간색 (한국 증시)
        totalProfitLoss < 0 -> Color(0xFF1565C0)  // 하락: 파란색
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "포트폴리오 요약",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침", modifier = Modifier.size(20.dp))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryItem(label = "매입 금액", value = "${String.format("%,.0f", totalBuyAmount)}원")
                SummaryItem(label = "평가 금액", value = "${String.format("%,.0f", totalEvaluationAmount)}원")
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("평가 손익", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "${if (totalProfitLoss >= 0) "+" else ""}${String.format("%,.0f", totalProfitLoss)}원",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = profitColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("수익률", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "${if (totalProfitRate >= 0) "+" else ""}${String.format("%.2f", totalProfitRate)}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = profitColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PendingOrdersBanner(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                text = "확인 대기 중인 주문 ${count}개 - 탭하여 확인",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String?, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PortfolioItemCard(portfolio: Portfolio) {
    val profitColor = when {
        portfolio.profitLoss > 0 -> Color(0xFFE53935)
        portfolio.profitLoss < 0 -> Color(0xFF1565C0)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(portfolio.stockName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${portfolio.quantity}주 | 평균 ${String.format("%,.0f", portfolio.averageBuyPrice)}원",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${String.format("%,.0f", portfolio.currentPrice)}원",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${if (portfolio.profitRate >= 0) "+" else ""}${String.format("%.2f", portfolio.profitRate)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = profitColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RecommendedStockCard(stock: RecommendedStock) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stock.stockName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "목표 +${String.format("%.1f", stock.expectedReturnRate)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE53935)
                )
            }
            Text(
                text = "현재가: ${String.format("%,.0f", stock.currentPrice)}원 | 점수: ${String.format("%.0f", stock.score)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stock.recommendReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun TradingRecordCard(record: TradingRecord) {
    val typeColor = if (record.tradeType.name == "BUY") Color(0xFFE53935) else Color(0xFF1565C0)
    val typeLabel = if (record.tradeType.name == "BUY") "매수" else "매도"

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = typeColor.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = typeLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = typeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(record.stockName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${record.quantity}주 @ ${String.format("%,.0f", record.price)}원",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${String.format("%,.0f", record.totalAmount)}원",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = record.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApiNotConfiguredCard(onNavigateToSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "초기 설정이 필요합니다",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "KIS Open API 키와 계좌 정보를 설정해야 앱을 사용할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onNavigateToSettings) {
                Text("설정하러 가기")
            }
        }
    }
}
