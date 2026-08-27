package com.shisan.campuspro.feature.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shisan.campuspro.core.data.AuthRepository
import com.shisan.campuspro.core.data.GradesRepository
import com.shisan.campuspro.core.data.SyncRepository
import com.shisan.campuspro.core.designsystem.CampusEmptyState
import com.shisan.campuspro.core.designsystem.CampusColors
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.model.GpaCalculator
import com.shisan.campuspro.core.model.Grade
import com.shisan.campuspro.core.model.SyncType
import com.shisan.campuspro.core.model.formatSemesterLabel
import com.shisan.campuspro.core.model.userMessage
import com.shisan.campuspro.core.ui.GradeCard
import com.shisan.campuspro.core.ui.CampusWindowWidthClass
import com.shisan.campuspro.core.ui.LocalCampusAdaptiveInfo
import com.shisan.campuspro.core.ui.StatsCard
import com.shisan.campuspro.core.ui.StatsItem
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

data class GradesUiState(
    val grades: List<Grade> = emptyList(),
    val visibleGrades: List<Grade> = emptyList(),
    val isLoggedIn: Boolean = false,
    val syncing: Boolean = false,
    val syncMessage: String? = null,
    val selectedSemester: String? = null,
    val semesters: List<String> = emptyList(),
    val courseQuery: String = "",
)

internal enum class SemesterFilterLayout { Horizontal, Vertical }

internal fun semesterFilterLayout(widthClass: CampusWindowWidthClass): SemesterFilterLayout =
    if (widthClass == CampusWindowWidthClass.Expanded) {
        SemesterFilterLayout.Vertical
    } else {
        SemesterFilterLayout.Horizontal
    }

// ────────────────────────────────────────────────
// ViewModel
// ────────────────────────────────────────────────

class GradesViewModel(
    private val repository: GradesRepository,
    private val syncRepository: SyncRepository,
    authRepository: AuthRepository,
) : ViewModel() {
    private val _syncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)
    private val _selectedSemester = MutableStateFlow<SemesterSelection>(SemesterSelection.Latest)
    private val _courseQuery = MutableStateFlow("")
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    val uiState: StateFlow<GradesUiState> = combine(
        repository.observeGrades(),
        authRepository.authState,
        syncing,
        _syncMessage,
        combine(_selectedSemester, _courseQuery, ::Pair),
    ) { grades, authState, syncing, syncMessage, filter ->
        val (selectedSemester, courseQuery) = filter
        // 学期按时间倒序排列（新学期在最前），用户预期最新学期显示在最上面；
        // "2025-2026-1" 这类学期字符串的字典序即时间序，sorted() 后 reversed() 即可。
        val semesters = grades.mapNotNull { it.semester }.distinct().sorted().reversed()
        val effectiveSemester = when (selectedSemester) {
            SemesterSelection.All -> null
            SemesterSelection.Latest -> semesters.firstOrNull()
            is SemesterSelection.Specific -> selectedSemester.semester
                .takeIf { it in semesters } ?: semesters.firstOrNull()
        }
        val semesterGrades = if (effectiveSemester == null) {
            grades
        } else {
            grades.filter { it.semester == effectiveSemester }
        }
        val visibleGrades = semesterGrades.filter {
            courseQuery.isBlank() || it.name.contains(courseQuery.trim(), ignoreCase = true)
        }
        GradesUiState(
            grades = if (authState.isLoggedIn) grades else emptyList(),
            visibleGrades = if (authState.isLoggedIn) visibleGrades else emptyList(),
            isLoggedIn = authState.isLoggedIn,
            syncing = syncing,
            syncMessage = syncMessage,
            selectedSemester = effectiveSemester,
            semesters = semesters,
            courseQuery = courseQuery,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GradesUiState())

    fun sync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            _syncMessage.value = null
            try {
                val result = syncRepository.syncByType(SyncType.GRADES)
                _syncMessage.value = if (result.success) {
                    if (result.count > 0) "已同步 ${result.count} 条成绩" else "同步完成，未获取到成绩"
                } else {
                    result.failureReason?.userMessage() ?: result.error ?: "同步成绩失败"
                }
            } finally {
                _syncing.value = false
            }
        }
    }

    fun selectSemester(semester: String?) {
        _selectedSemester.value = semester?.let(SemesterSelection::Specific) ?: SemesterSelection.All
    }

    fun updateCourseQuery(query: String) {
        _courseQuery.value = query
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}

private sealed interface SemesterSelection {
    data object Latest : SemesterSelection
    data object All : SemesterSelection
    data class Specific(val semester: String) : SemesterSelection
}

// ────────────────────────────────────────────────
// Route
// ────────────────────────────────────────────────

@Composable
fun GradesRoute(
    repository: GradesRepository,
    syncRepository: SyncRepository,
    authRepository: AuthRepository,
    syncRequestId: Long?,
    onSyncRequestConsumed: (Long) -> Unit,
    onSyncRequested: () -> Unit,
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: GradesViewModel = viewModel(factory = GradesViewModelFactory(repository, syncRepository, authRepository))
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

    GradesScreen(
        uiState = uiState,
        onSync = onSyncRequested,
        onSemesterSelected = viewModel::selectSemester,
        modifier = modifier,
    )
}

// ────────────────────────────────────────────────
// Screen
// ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(
    uiState: GradesUiState,
    onSync: () -> Unit,
    onSemesterSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filterLayout = semesterFilterLayout(LocalCampusAdaptiveInfo.current.widthClass)
    val filteredGrades by remember(uiState.visibleGrades) {
        derivedStateOf { uiState.visibleGrades }
    }

    // 统计数据：与教务系统“平均学分绩点”一致，按学分加权而非简单平均，
    // 否则高学分课程（如高数 6 学分）的低绩点会被低学分课程稀释，结果偏高于官方值。
    val totalGpa by remember(filteredGrades) {
        derivedStateOf {
            val weighted = GpaCalculator.creditWeightedGpa(filteredGrades)
            when {
                weighted != null -> String.format("%.2f", weighted)
                filteredGrades.isEmpty() -> "—"
                else -> String.format("%.2f", filteredGrades.map { it.gpa }.average())
            }
        }
    }
    val totalCredits by remember(filteredGrades) {
        derivedStateOf {
            if (filteredGrades.isEmpty()) "—"
            else String.format("%.1f", filteredGrades.sumOf { it.credits })
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("成绩") },
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
            if (uiState.grades.isEmpty() && !uiState.syncing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    CampusEmptyState(
                        title = "还没有成绩",
                        message = "下拉刷新同步教务系统，成绩会显示在这里。",
                        icon = CampusIcons.Check,
                    )
                }
            } else {
                if (LocalCampusAdaptiveInfo.current.usesTwoPane) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().widthIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GradesOverview(
                                totalGpa = totalGpa,
                                totalCredits = totalCredits,
                                courseCount = filteredGrades.size,
                                semesters = uiState.semesters,
                                selectedSemester = uiState.selectedSemester,
                                onSemesterSelected = onSemesterSelected,
                                filterLayout = filterLayout,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        GradesList(
                            grades = filteredGrades,
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
                    // 统计卡片
                    item(key = "stats") {
                        GradesOverview(
                            totalGpa = totalGpa,
                            totalCredits = totalCredits,
                            courseCount = filteredGrades.size,
                            semesters = uiState.semesters,
                            selectedSemester = uiState.selectedSemester,
                            onSemesterSelected = onSemesterSelected,
                            filterLayout = SemesterFilterLayout.Horizontal,
                        )
                    }

                    // 成绩列表
                    if (filteredGrades.isEmpty()) {
                        item(key = "empty_filter") {
                            Text(
                                text = "该学期暂无成绩",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 32.dp),
                            )
                        }
                    } else {
                        items(filteredGrades, key = { it.id }) { grade ->
                            GradeCard(grade)
                        }
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
private fun GradesOverview(
    totalGpa: String,
    totalCredits: String,
    courseCount: Int,
    semesters: List<String>,
    selectedSemester: String?,
    onSemesterSelected: (String?) -> Unit,
    filterLayout: SemesterFilterLayout,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatsCard(
            items = listOf(
                StatsItem(totalGpa, "总 GPA"),
                StatsItem("$courseCount", "课程数"),
                StatsItem(totalCredits, "学分"),
            ),
        )
        if (semesters.isNotEmpty()) {
            if (filterLayout == SemesterFilterLayout.Vertical) {
                Text("学期筛选", style = MaterialTheme.typography.titleSmall)
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "all") {
                        SemesterFilterChip(
                            label = "全部",
                            selected = selectedSemester == null,
                            onClick = { onSemesterSelected(null) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    items(semesters, key = { it }) { semester ->
                        SemesterFilterChip(
                            label = formatSemesterLabel(semester),
                            selected = selectedSemester == semester,
                            onClick = { onSemesterSelected(semester) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "all") {
                        SemesterFilterChip(
                            label = "全部",
                            selected = selectedSemester == null,
                            onClick = { onSemesterSelected(null) },
                        )
                    }
                    items(semesters, key = { it }) { semester ->
                        SemesterFilterChip(
                            label = formatSemesterLabel(semester),
                            selected = selectedSemester == semester,
                            onClick = { onSemesterSelected(semester) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SemesterFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CampusColors.ChipSelectedContainer,
            selectedLabelColor = CampusColors.ChipSelectedOnContainer,
        ),
    )
}

@Composable
private fun GradesList(
    grades: List<Grade>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (grades.isEmpty()) {
            item { Text("该学期暂无成绩", modifier = Modifier.padding(32.dp)) }
        } else {
            items(grades, key = { it.id }) { grade -> GradeCard(grade) }
        }
    }
}

// ────────────────────────────────────────────────
// Factory
// ────────────────────────────────────────────────

private class GradesViewModelFactory(
    private val repository: GradesRepository,
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        GradesViewModel(repository, syncRepository, authRepository) as T
}
