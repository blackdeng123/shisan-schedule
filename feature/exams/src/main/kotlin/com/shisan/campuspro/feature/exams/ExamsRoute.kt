package com.shisan.campuspro.feature.exams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shisan.campuspro.core.data.AuthRepository
import com.shisan.campuspro.core.data.ExamsRepository
import com.shisan.campuspro.core.data.SyncRepository
import com.shisan.campuspro.core.designsystem.CampusEmptyState
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.model.Exam
import com.shisan.campuspro.core.model.SyncType
import com.shisan.campuspro.core.model.userMessage
import com.shisan.campuspro.core.ui.ExamCard
import com.shisan.campuspro.core.ui.LocalCampusAdaptiveInfo
import com.shisan.campuspro.core.ui.usesTwoPane
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ────────────────────────────────────────────────
// UiState
// ────────────────────────────────────────────────

data class ExamsUiState(
    val exams: List<Exam> = emptyList(),
    val isLoggedIn: Boolean = false,
    val syncing: Boolean = false,
    val syncMessage: String? = null,
)

// ────────────────────────────────────────────────
// ViewModel
// ────────────────────────────────────────────────

class ExamsViewModel(
    private val repository: ExamsRepository,
    private val syncRepository: SyncRepository,
    authRepository: AuthRepository,
) : ViewModel() {
    private val _syncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    val uiState: StateFlow<ExamsUiState> = combine(
        repository.observeExams(),
        authRepository.authState,
        syncing,
        _syncMessage,
    ) { exams, authState, syncing, syncMessage ->
        ExamsUiState(exams = if (authState.isLoggedIn) exams else emptyList(), isLoggedIn = authState.isLoggedIn, syncing = syncing, syncMessage = syncMessage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExamsUiState())

    fun sync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            _syncMessage.value = null
            try {
                val result = syncRepository.syncByType(SyncType.EXAMS)
                _syncMessage.value = if (result.success) {
                    if (result.count > 0) "已同步 ${result.count} 场考试" else "同步完成，未获取到考试安排"
                } else {
                    result.failureReason?.userMessage() ?: result.error ?: "同步考试失败"
                }
            } finally {
                _syncing.value = false
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}

// ────────────────────────────────────────────────
// Route
// ────────────────────────────────────────────────

@Composable
fun ExamsRoute(
    repository: ExamsRepository,
    syncRepository: SyncRepository,
    authRepository: AuthRepository,
    syncRequestId: Long?,
    onSyncRequestConsumed: (Long) -> Unit,
    onSyncRequested: () -> Unit,
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: ExamsViewModel = viewModel(factory = ExamsViewModelFactory(repository, syncRepository, authRepository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(syncRequestId) {
        val id = syncRequestId ?: return@LaunchedEffect
        viewModel.sync()
        onSyncRequestConsumed(id)
    }

    // Snackbar 驱动
    LaunchedEffect(uiState.syncMessage) {
        val message = uiState.syncMessage ?: return@LaunchedEffect
        onShowMessage(message)
        viewModel.clearSyncMessage()
    }

    ExamsScreen(
        uiState = uiState,
        onSync = onSyncRequested,
        modifier = modifier,
    )
}

// ────────────────────────────────────────────────
// Screen
// ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    uiState: ExamsUiState,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 排序：按 daysLeft 升序，已结束（<0）排最后
    val sortedExams by remember(uiState.exams) {
        derivedStateOf {
            uiState.exams.sortedWith(compareBy<Exam> { it.daysLeft < 0 }.thenBy { it.daysLeft })
        }
    }

    // 最近考试概览
    val nextExam by remember(sortedExams) {
        derivedStateOf { sortedExams.firstOrNull { it.daysLeft >= 0 } }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("考试") },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.syncing,
            onRefresh = onSync,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.exams.isEmpty() && !uiState.syncing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    CampusEmptyState(
                        title = "还没有考试安排",
                        message = "下拉刷新同步教务系统，考试安排会显示在这里。",
                        icon = CampusIcons.Exams,
                    )
                }
            } else {
                if (LocalCampusAdaptiveInfo.current.usesTwoPane) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f).widthIn(max = 400.dp)) {
                            ExamOverviewCard(nextExam = nextExam, totalCount = uiState.exams.size)
                        }
                        ExamList(
                            exams = sortedExams,
                            modifier = Modifier.weight(2f),
                        )
                    }
                } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 顶部概览卡片
                    item(key = "overview") {
                        ExamOverviewCard(
                            nextExam = nextExam,
                            totalCount = uiState.exams.size,
                        )
                    }

                    // 考试列表
                    items(sortedExams, key = { it.id }) { exam ->
                        ExamCard(exam)
                    }

                    // 底部间距
                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(16.dp))
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun ExamList(
    exams: List<Exam>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(exams, key = { it.id }) { exam -> ExamCard(exam) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ────────────────────────────────────────────────
// 概览卡片
// ────────────────────────────────────────────────

@Composable
private fun ExamOverviewCard(
    nextExam: Exam?,
    totalCount: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (nextExam != null) {
                val daysText = when {
                    nextExam.daysLeft == 0 -> "今天"
                    nextExam.daysLeft == 1 -> "明天"
                    else -> "还有 ${nextExam.daysLeft} 天"
                }
                Text(
                    text = "距离最近考试",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = daysText,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = nextExam.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Text(
                    text = "暂无待考科目",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = "共 $totalCount 场考试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
            )
        }
    }
}

// ────────────────────────────────────────────────
// Factory
// ────────────────────────────────────────────────

private class ExamsViewModelFactory(
    private val repository: ExamsRepository,
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ExamsViewModel(repository, syncRepository, authRepository) as T
}
