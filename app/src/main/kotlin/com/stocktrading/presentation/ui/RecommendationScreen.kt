package com.stocktrading.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stocktrading.data.model.IndicatorSnapshot
import com.stocktrading.data.model.RecommendedStock
import com.stocktrading.presentation.viewmodel.DashboardViewModel

/**
 * 추천 종목 화면
 * 오늘의 추천 종목 상세 내용 표시
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    onBack: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("오늘의 추천 종목") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (uiState.isRecommendationRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.refreshRecommendations() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "추천 종목 새로고침"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        if (uiState.recommendations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "추천 종목이 없습니다",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "매일 오전 8시 자동 분석 또는 우측 상단 버튼으로 직접 새로고침",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.refreshRecommendations() },
                        enabled = !uiState.isRecommendationRefreshing
                    ) {
                        if (uiState.isRecommendationRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("지금 분석하기")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "AI 분석 기반 추천 종목",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(uiState.recommendations, key = { it.stockCode }) { stock ->
                    RecommendationDetailCard(stock = stock)
                }
                item {
                    Text(
                        text = "※ 투자 판단은 본인 책임입니다. 위 정보는 참고용입니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 에러 메시지 Snackbar — 콘텐츠 위에 오버레이
        uiState.errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("닫기")
                    }
                }
            ) {
                Text(msg, style = MaterialTheme.typography.bodySmall)
            }
        }
        } // Box 닫기
    }
}

@Composable
private fun RecommendationDetailCard(stock: RecommendedStock) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(stock.stockName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stock.stockCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${String.format("%,.0f", stock.currentPrice)}원",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "분석 점수: ${String.format("%.1f", stock.score)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider()

            // 목표/손절 가격
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PriceTargetItem(
                    label = "목표가",
                    price = stock.targetPrice,
                    rate = stock.expectedReturnRate,
                    isPositive = true
                )
                PriceTargetItem(
                    label = "손절가",
                    price = stock.stopLossPrice,
                    rate = -5.0,
                    isPositive = false
                )
            }

            // 추천 근거
            Text(
                text = "추천 근거",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stock.recommendReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 기술적 지표
            IndicatorSummaryRow(indicators = stock.indicators)
        }
    }
}

@Composable
private fun PriceTargetItem(label: String, price: Double, rate: Double, isPositive: Boolean) {
    val color = if (isPositive) Color(0xFFE53935) else Color(0xFF1565C0)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "${String.format("%,.0f", price)}원",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(
            text = "${if (isPositive) "+" else ""}${String.format("%.1f", rate)}%",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun IndicatorSummaryRow(indicators: IndicatorSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("기술적 지표", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IndicatorChip(label = "RSI", value = String.format("%.1f", indicators.rsi))
            IndicatorChip(label = "MACD", value = String.format("%.2f", indicators.macd))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IndicatorChip(label = "MA5", value = String.format("%,.0f", indicators.ma5))
            IndicatorChip(label = "MA20", value = String.format("%,.0f", indicators.ma20))
            IndicatorChip(label = "MA60", value = String.format("%,.0f", indicators.ma60))
        }
    }
}

@Composable
private fun IndicatorChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
