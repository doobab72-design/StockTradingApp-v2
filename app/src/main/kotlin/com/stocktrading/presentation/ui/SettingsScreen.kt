package com.stocktrading.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stocktrading.presentation.viewmodel.SettingsViewModel

/**
 * 설정 화면
 * KIS API 키, 계좌 정보, 모의/실전 투자 설정
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 거래 모드 설정
            item {
                TradingModeSection(
                    isMockMode = uiState.isMockMode,
                    onModeChange = viewModel::setMockMode
                )
            }

            // KIS API 키 설정
            item {
                ApiKeySection(
                    appKey = uiState.appKey,
                    appSecret = uiState.appSecret,
                    onAppKeyChange = viewModel::updateAppKey,
                    onAppSecretChange = viewModel::updateAppSecret,
                    onSave = viewModel::saveApiKeys
                )
            }

            // 계좌 정보 설정
            item {
                AccountSection(
                    accountNo = uiState.accountNo,
                    accountProductCode = uiState.accountProductCode,
                    onAccountNoChange = viewModel::updateAccountNo,
                    onProductCodeChange = viewModel::updateAccountProductCode,
                    onSave = viewModel::saveAccountInfo
                )
            }

            // Worker 수동 트리거 (개발/테스트용)
            item {
                WorkerControlSection(
                    onTriggerRecommendation = viewModel::triggerDailyRecommendation,
                    onTriggerAutoTrading = viewModel::triggerAutoTrading
                )
            }

            // 계정 초기화
            item {
                DangerZoneSection(onClearAll = viewModel::clearAllCredentials)
            }
        }
    }
}

@Composable
private fun TradingModeSection(isMockMode: Boolean, onModeChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("거래 모드", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isMockMode) "모의투자" else "실전투자",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (isMockMode) "모의투자 API 사용 (안전)" else "⚠️ 실제 주문이 발생합니다!",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isMockMode) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                }
                Switch(
                    checked = !isMockMode,  // true = 실전
                    onCheckedChange = { onModeChange(!it) }  // Switch ON = 실전
                )
            }
        }
    }
}

@Composable
private fun ApiKeySection(
    appKey: String,
    appSecret: String,
    onAppKeyChange: (String) -> Unit,
    onAppSecretChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var showSecret by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("KIS API 키", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = appKey,
                onValueChange = onAppKeyChange,
                label = { Text("앱 키 (App Key)") },
                placeholder = { Text("KIS에서 발급받은 앱 키") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) }
            )

            OutlinedTextField(
                value = appSecret,
                onValueChange = onAppSecretChange,
                label = { Text("앱 시크릿 (App Secret)") },
                placeholder = { Text("KIS에서 발급받은 앱 시크릿") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showSecret = !showSecret }) {
                        Icon(
                            if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showSecret) "숨기기" else "보기"
                        )
                    }
                }
            )

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("API 키 저장")
            }
        }
    }
}

@Composable
private fun AccountSection(
    accountNo: String,
    accountProductCode: String,
    onAccountNoChange: (String) -> Unit,
    onProductCodeChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("계좌 정보", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = accountNo,
                onValueChange = onAccountNoChange,
                label = { Text("계좌번호 (앞 8자리)") },
                placeholder = { Text("예: 12345678") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) }
            )

            OutlinedTextField(
                value = accountProductCode,
                onValueChange = onProductCodeChange,
                label = { Text("계좌상품코드 (뒤 2자리)") },
                placeholder = { Text("예: 01") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("계좌 정보 저장")
            }
        }
    }
}

@Composable
private fun WorkerControlSection(
    onTriggerRecommendation: () -> Unit,
    onTriggerAutoTrading: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("수동 실행 (개발/테스트)", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Worker를 즉시 실행합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTriggerRecommendation, modifier = Modifier.weight(1f)) {
                    Text("추천 분석", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = onTriggerAutoTrading, modifier = Modifier.weight(1f)) {
                    Text("매매 분석", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DangerZoneSection(onClearAll: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("위험 구역", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            OutlinedButton(
                onClick = { showDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("모든 설정 초기화")
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("설정 초기화") },
            text = { Text("API 키, 계좌 정보 등 모든 설정이 삭제됩니다. 계속하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("초기화")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("취소") }
            }
        )
    }
}
