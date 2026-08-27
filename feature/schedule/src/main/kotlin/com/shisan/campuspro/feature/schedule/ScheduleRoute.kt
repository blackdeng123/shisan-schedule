package com.shisan.campuspro.feature.schedule

// 界面与交互参考：https://github.com/YZune/WakeupSchedule_Kotlin
// WakeUp课程表采用 Apache-2.0；当前页面已使用 Jetpack Compose 重写。

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shisan.campuspro.core.data.AuthRepository
import com.shisan.campuspro.core.data.ScheduleRepository
import com.shisan.campuspro.core.data.SyncRepository
import com.shisan.campuspro.core.data.normalizeCachedCourseColors
import com.shisan.campuspro.core.designsystem.CampusColors
import com.shisan.campuspro.core.designsystem.CampusCourseColors
import com.shisan.campuspro.core.designsystem.CampusEmptyState
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.designsystem.CampusTheme
import com.shisan.campuspro.core.designsystem.LocalCampusDarkTheme
import com.shisan.campuspro.core.model.AuthSessionStatus
import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import com.shisan.campuspro.core.model.Schedule
import com.shisan.campuspro.core.model.SyncType
import com.shisan.campuspro.core.model.canAccessCachedData
import com.shisan.campuspro.core.model.userMessage
import com.shisan.campuspro.core.ui.AdaptiveTwoPane
import com.shisan.campuspro.core.ui.LocalCampusAdaptiveInfo
import com.shisan.campuspro.core.ui.WeeklyCourseGrid
import com.shisan.campuspro.core.ui.usesTwoPane
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val DateTitleFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")

@Composable
private fun scheduleBackgroundBrush(): Brush {
    val darkTheme = LocalCampusDarkTheme.current
    return Brush.verticalGradient(
        if (darkTheme) {
            listOf(
                Color(0xFF07111F),
                Color(0xFF0F1E33),
                Color(0xFF162A45),
            )
        } else {
            listOf(
                Color(0xFFE9ECF8),
                Color(0xFFDDE8F7),
                Color(0xFFC8D9EE),
            )
        },
    )
}

data class ScheduleUiState(
    val schedules: List<Schedule> = emptyList(),
    val activeSchedule: Schedule? = null,
    val isLoggedIn: Boolean = false,
    val syncing: Boolean = false,
    val syncMessage: String? = null,
    val selectedWeek: Int = 1,
    val currentWeek: Int = 1,
    val showStartDatePrompt: Boolean = false,
    val initialLoading: Boolean = true,
    val showingOfflineCache: Boolean = false,
    val termPhase: TermPhase = TermPhase.InProgress,
)

class ScheduleViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val syncRepository: SyncRepository,
    authRepository: AuthRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _syncing = MutableStateFlow(false)
    private val _selectedWeek = MutableStateFlow<Int?>(null)
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private val weekAndMessage = combine(_selectedWeek, _syncMessage) { selectedWeek, syncMessage ->
        selectedWeek to syncMessage
    }

    init {
        viewModelScope.launch {
            normalizeCachedCourseColors(scheduleRepository)
        }
    }

    val uiState: StateFlow<ScheduleUiState> = combine(
        scheduleRepository.observeSchedules(),
        scheduleRepository.observeActiveSchedule(),
        authRepository.authState,
        syncing,
        weekAndMessage,
    ) { schedules, active, authState, syncing, weekAndMessage ->
        val (selectedWeek, syncMessage) = weekAndMessage
        val totalWeeks = active?.totalWeeks?.coerceAtLeast(1) ?: 1
        val today = todayProvider()
        val currentWeek = inferSelectedWeek(active, today = today)
        // 数据加载后立即固定当前周，避免被初始 pager 页面的 onPageChange 覆盖
        if (selectedWeek == null && active != null) {
            _selectedWeek.value = currentWeek
        }
        val canAccessCache = authState.sessionStatus.canAccessCachedData
        val phase = if (canAccessCache) termPhase(active, today = today) else TermPhase.InProgress
        ScheduleUiState(
            schedules = if (canAccessCache) schedules else emptyList(),
            activeSchedule = if (canAccessCache) active else null,
            isLoggedIn = authState.isLoggedIn,
            syncing = syncing,
            syncMessage = syncMessage,
            selectedWeek = selectedWeek?.coerceIn(1, totalWeeks) ?: currentWeek,
            currentWeek = currentWeek,
            // 阶段提示条（未开学/已结束）已引导修改开学日期，避免两条提示叠加
            showStartDatePrompt = canAccessCache &&
                active != null &&
                !active.startDateConfirmed &&
                active.startDate.isNotBlank() &&
                phase == TermPhase.InProgress,
            initialLoading = !authState.sessionResolved &&
                authState.sessionStatus == AuthSessionStatus.UNAUTHENTICATED,
            showingOfflineCache = authState.sessionStatus == AuthSessionStatus.OFFLINE_AUTHENTICATED && active != null,
            termPhase = phase,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    fun sync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            _syncMessage.value = null
            try {
                val result = syncRepository.syncByType(SyncType.SCHEDULE)
                _syncMessage.value = if (result.success) {
                    if (result.count > 0) "已同步 ${result.count} 门课程" else "同步完成，未获取到课程"
                } else {
                    result.failureReason?.userMessage() ?: result.error ?: "同步课程失败"
                }
            } finally {
                _syncing.value = false
            }
        }
    }

    fun selectSchedule(name: String) {
        val schedule = uiState.value.schedules.firstOrNull { it.name == name } ?: return
        _selectedWeek.value = null
        viewModelScope.launch { scheduleRepository.selectSchedule(schedule.id) }
    }

    fun selectWeek(week: Int) {
        val totalWeeks = uiState.value.activeSchedule?.totalWeeks?.coerceAtLeast(1) ?: 1
        _selectedWeek.value = week.coerceIn(1, totalWeeks)
    }

    fun previousWeek() {
        selectRelativeWeek(delta = -1)
    }

    fun nextWeek() {
        selectRelativeWeek(delta = 1)
    }

    private fun selectRelativeWeek(delta: Int) {
        val state = uiState.value
        val totalWeeks = state.activeSchedule?.totalWeeks?.coerceAtLeast(1) ?: 1
        _selectedWeek.value = (state.selectedWeek + delta).coerceIn(1, totalWeeks)
    }

    fun deleteCourse(courseId: String) {
        val scheduleId = uiState.value.activeSchedule?.id ?: return
        viewModelScope.launch { scheduleRepository.deleteCourse(scheduleId, courseId) }
    }

    fun createSchedule(name: String) {
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            val schedule = com.shisan.campuspro.core.data.createEmptySchedule(id).copy(name = name)
            scheduleRepository.createSchedule(schedule)
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch { scheduleRepository.deleteSchedule(scheduleId) }
    }

    fun updateScheduleStartDate(startDate: String) {
        val schedule = uiState.value.activeSchedule ?: return
        viewModelScope.launch {
            scheduleRepository.upsertSchedule(
                schedule.copy(startDate = startDate, startDateConfirmed = true)
            )
        }
    }

    fun confirmStartDate() {
        val schedule = uiState.value.activeSchedule ?: return
        if (!schedule.startDateConfirmed) {
            viewModelScope.launch {
                scheduleRepository.upsertSchedule(schedule.copy(startDateConfirmed = true))
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}

@Composable
fun ScheduleRoute(
    scheduleRepository: ScheduleRepository,
    syncRepository: SyncRepository,
    authRepository: AuthRepository,
    syncRequestId: Long?,
    onSyncRequestConsumed: (Long) -> Unit,
    onSyncRequested: () -> Unit,
    onNavigateToCourseEdit: (scheduleId: String, courseId: String?) -> Unit,
    onOpenScheduleSettings: () -> Unit = {},
    onOpenClassTimeSettings: () -> Unit = {},
    onShare: () -> Unit = {},
    onExportIcs: () -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: ScheduleViewModel = viewModel(
        factory = ScheduleViewModelFactory(scheduleRepository, syncRepository, authRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(syncRequestId) {
        val id = syncRequestId ?: return@LaunchedEffect
        viewModel.sync()
        onSyncRequestConsumed(id)
    }
    LaunchedEffect(uiState.syncMessage) {
        val message = uiState.syncMessage ?: return@LaunchedEffect
        onShowMessage(message)
        viewModel.clearSyncMessage()
    }
    ScheduleScreen(
        uiState = uiState,
        onSync = onSyncRequested,
        onScheduleSelected = viewModel::selectSchedule,
        onWeekSelected = viewModel::selectWeek,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onAddCourse = {
            val scheduleId = uiState.activeSchedule?.id ?: return@ScheduleScreen
            onNavigateToCourseEdit(scheduleId, null)
        },
        onEditCourse = { course ->
            val scheduleId = uiState.activeSchedule?.id ?: return@ScheduleScreen
            onNavigateToCourseEdit(scheduleId, course.id)
        },
        onDeleteCourse = viewModel::deleteCourse,
        onCreateSchedule = viewModel::createSchedule,
        onDeleteSchedule = viewModel::deleteSchedule,
        onOpenScheduleSettings = onOpenScheduleSettings,
        onOpenClassTimeSettings = onOpenClassTimeSettings,
        onShare = onShare,
        onExportIcs = onExportIcs,
        onConfirmStartDate = viewModel::confirmStartDate,
        onUpdateStartDate = viewModel::updateScheduleStartDate,
        modifier = modifier,
    )
}

// ────────────────────────────────────────────────
// 主界面 —— 仿 WakeupSchedule 布局
// ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    uiState: ScheduleUiState,
    onSync: () -> Unit,
    onScheduleSelected: (String) -> Unit,
    onWeekSelected: (Int) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onAddCourse: () -> Unit = {},
    onEditCourse: (Course) -> Unit = {},
    onDeleteCourse: (String) -> Unit = {},
    onCreateSchedule: (String) -> Unit = {},
    onDeleteSchedule: (String) -> Unit = {},
    onOpenScheduleSettings: () -> Unit = {},
    onOpenClassTimeSettings: () -> Unit = {},
    onShare: () -> Unit = {},
    onExportIcs: () -> Unit = {},
    onConfirmStartDate: () -> Unit = {},
    onUpdateStartDate: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activeSchedule = uiState.activeSchedule
    val totalWeeks = activeSchedule?.totalWeeks?.coerceAtLeast(1) ?: 1

    // HorizontalPager 状态 —— 跟随 selectedWeek
    val pagerState = rememberPagerState(
        initialPage = (uiState.selectedWeek - 1).coerceAtLeast(0),
        pageCount = { totalWeeks },
    )
    val scope = rememberCoroutineScope()

    // 同步 ViewModel → pager
    var lastPagerScheduleId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeSchedule?.id, uiState.selectedWeek, totalWeeks) {
        val target = (uiState.selectedWeek - 1).coerceIn(0, totalWeeks - 1)
        if (target != pagerState.settledPage) {
            if (lastPagerScheduleId != activeSchedule?.id) {
                pagerState.scrollToPage(target)
            } else {
                pagerState.animateScrollToPage(target)
            }
        }
        lastPagerScheduleId = activeSchedule?.id
    }

    var selectedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showCourseDetail by remember { mutableStateOf<Course?>(null) }
    var showNewScheduleDialog by remember { mutableStateOf(false) }
    var showStartDateDialog by remember { mutableStateOf(false) }
    val adaptiveInfo = LocalCampusAdaptiveInfo.current

    // 切换课表时检查是否需要确认开学日期
    LaunchedEffect(activeSchedule?.id, activeSchedule?.startDateConfirmed) {
        if (activeSchedule != null && !activeSchedule.startDateConfirmed) {
            showStartDateDialog = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── 顶部栏 ──
        StaggeredEnterItem(delayMillis = 0) {
            ScheduleTopBar(
                dateText = LocalDate.now().format(DateTitleFormatter),
                weekText = "第 ${uiState.selectedWeek} 周",
                onAddCourse = onAddCourse,
                onShare = onShare,
                onMoreClick = { showMoreSheet = true },
            )
        }

        // ── 开学日期确认提示 ──
        if (uiState.showingOfflineCache) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "登录验证失败，当前展示缓存数据",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        // ── 学期阶段提示：未开学 / 已结束 ──
        when (val phase = uiState.termPhase) {
            is TermPhase.NotStarted -> TermPhasePrompt(
                text = buildString {
                    append("还未到开学时间：${phase.startDate.monthValue}月${phase.startDate.dayOfMonth}日开学")
                    if (phase.daysUntilStart > 0) append("，还有 ${phase.daysUntilStart} 天")
                    append("，点击可修改开学日期")
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = { showStartDateDialog = true },
            )
            TermPhase.Ended -> TermPhasePrompt(
                text = "该学期已结束，当前展示的是历史课表",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { showStartDateDialog = true },
            )
            TermPhase.InProgress -> Unit
        }

        if (uiState.showStartDatePrompt) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { showStartDateDialog = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = "开学日期似乎不准确？点击设置正确日期，课表周数会自动校准",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        // ── HorizontalPager：横滑切周（PullToRefreshBox 包裹）──
        PullToRefreshBox(
            isRefreshing = uiState.syncing,
            onRefresh = onSync,
            modifier = Modifier.fillMaxSize().weight(1f),
        ) {
        AdaptiveTwoPane(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (LocalCampusAdaptiveInfo.current.usesTwoPane) 16.dp else 0.dp),
            mainPane = {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { "week-${it + 1}" },
        ) { page ->
            val week = page + 1
            val allCourses = activeSchedule?.courses.orEmpty()
            val weekDates = remember(activeSchedule, week) {
                selectedWeekDates(activeSchedule, week)
            }
            val hasCourses = allCourses.isNotEmpty()

            if (uiState.initialLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                    )
                }
            } else if (hasCourses) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        WeeklyCourseGrid(
                            courses = allCourses,
                            selectedWeek = week,
                            weekDates = weekDates,
                            totalSlots = activeSchedule?.classTimeSlots?.size?.coerceAtLeast(10) ?: 10,
                            classTimeSlots = activeSchedule?.classTimeSlots.orEmpty(),
                            onCoursesClick = { courses ->
                                if (courses.size == 1) {
                                    showCourseDetail = courses.first()
                                } else {
                                    selectedCourses = courses
                                }
                            },
                            onEmptyAreaClick = {
                                if (adaptiveInfo.usesTwoPane) {
                                    showCourseDetail = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    StaggeredEnterItem(delayMillis = 100) {
                        CampusEmptyState(
                            title = "还没有课表",
                            message = "同步教务系统后，课程会显示在这里。",
                            icon = CampusIcons.Calendar,
                            actionText = if (uiState.syncing) "同步中..." else "同步课表",
                            onActionClick = onSync,
                        )
                    }
                }
            }
        }
            },
            supportingPane = {
                ScheduleSupportingPane(
                    course = showCourseDetail,
                    selectedWeek = uiState.selectedWeek,
                    totalCourses = activeSchedule?.courses.orEmpty().count {
                        it.weeks.isEmpty() || uiState.selectedWeek in it.weeks
                    },
                    onDelete = { course ->
                        onDeleteCourse(course.id)
                        showCourseDetail = null
                    },
                    onEdit = { course -> onEditCourse(course) },
                )
            },
        )
        } // PullToRefreshBox
    }

    // ── 更多操作 BottomSheet ──
    if (showMoreSheet) {
        ScheduleMoreSheet(
            schedules = uiState.schedules,
            activeSchedule = activeSchedule,
            selectedWeek = uiState.selectedWeek,
            totalWeeks = totalWeeks,
            currentWeek = uiState.currentWeek,
            syncing = uiState.syncing,
            onDismiss = { showMoreSheet = false },
            onScheduleSelected = {
                onScheduleSelected(it)
                showMoreSheet = false
            },
            onWeekSelected = { week ->
                onWeekSelected(week)
                scope.launch { pagerState.animateScrollToPage(week - 1) }
            },
            onCreateSchedule = {
                showMoreSheet = false
                showNewScheduleDialog = true
            },
            onDeleteSchedule = onDeleteSchedule,
            onOpenScheduleSettings = onOpenScheduleSettings,
            onOpenClassTimeSettings = onOpenClassTimeSettings,
            onExportIcs = onExportIcs,
            onSync = {
                showMoreSheet = false
                onSync()
            },
        )
    }

    // ── 同时间课程选择 ──
    if (selectedCourses.isNotEmpty()) {
        CourseSelectionDialog(
            courses = selectedCourses,
            selectedWeek = uiState.selectedWeek,
            onDismiss = { selectedCourses = emptyList() },
            onCourseSelected = {
                selectedCourses = emptyList()
                showCourseDetail = it
            },
        )
    }

    // ── 新建课表对话框 ──
    if (showNewScheduleDialog) {
        NewScheduleDialog(
            onDismiss = { showNewScheduleDialog = false },
            onConfirm = { name ->
                showNewScheduleDialog = false
                onCreateSchedule(name)
            },
        )
    }

    LaunchedEffect(pagerState.currentPage) {
        // 仅在有效课表加载后才同步 pager 滑动到 ViewModel
        if (activeSchedule != null) {
            onWeekSelected(pagerState.currentPage + 1)
        }
    }

    // ── 课程详情 BottomSheet ──
    if (!LocalCampusAdaptiveInfo.current.usesTwoPane) showCourseDetail?.let { course ->
        CourseDetailSheet(
            course = course,
            selectedWeek = uiState.selectedWeek,
            onDismiss = { showCourseDetail = null },
            onDelete = {
                onDeleteCourse(course.id)
                showCourseDetail = null
            },
            onEdit = {
                showCourseDetail = null
                onEditCourse(course)
            },
        )
    }

    // ── 开学日期确认弹窗 ──
    if (showStartDateDialog && activeSchedule != null) {
        StartDateConfirmDialog(
            currentStartDate = activeSchedule.startDate,
            onConfirm = {
                showStartDateDialog = false
                onConfirmStartDate()
            },
            onUpdate = { newDate ->
                showStartDateDialog = false
                onUpdateStartDate(newDate)
            },
            onDismiss = { showStartDateDialog = false },
        )
    }
}

@Composable
private fun ScheduleSupportingPane(
    course: Course?,
    selectedWeek: Int,
    totalCourses: Int,
    onDelete: (Course) -> Unit,
    onEdit: (Course) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        if (course == null) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("本周概览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("第 $selectedWeek 周", style = MaterialTheme.typography.headlineMedium)
                Text("共有 $totalCourses 门课程", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("选择课表中的课程可在这里查看详情。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            CourseDetailContent(
                course = course,
                selectedWeek = selectedWeek,
                onDelete = { onDelete(course) },
                onEdit = { onEdit(course) },
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
            )
        }
    }
}

// ────────────────────────────────────────────────
// 顶部栏 —— 仿 WakeupSchedule：大日期 + 周信息 + 图标按钮行
// ────────────────────────────────────────────────

@Composable
private fun ScheduleTopBar(
    dateText: String,
    weekText: String,
    onAddCourse: () -> Unit,
    onShare: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        // 左侧：日期 + 周次信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = weekText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 右侧：图标按钮行（仿 WakeupSchedule 一排小按钮）
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(
                contentDescription = "添加课程",
                onClick = onAddCourse,
                icon = CampusIcons.Add,
            )
            HeaderIconButton(
                contentDescription = "分享课表",
                onClick = onShare,
                icon = CampusIcons.Share,
            )
            HeaderIconButton(
                contentDescription = "更多",
                onClick = onMoreClick,
                icon = CampusIcons.More,
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(40.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 0.82f else 0.35f),
        )
    }
}

// ────────────────────────────────────────────────
// 底部操作面板 —— 仿 WakeupSchedule BottomSheet
// ────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ScheduleMoreSheet(
    schedules: List<Schedule>,
    activeSchedule: Schedule?,
    selectedWeek: Int,
    totalWeeks: Int,
    currentWeek: Int,
    syncing: Boolean,
    onDismiss: () -> Unit,
    onScheduleSelected: (String) -> Unit,
    onWeekSelected: (Int) -> Unit,
    onCreateSchedule: () -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onOpenScheduleSettings: () -> Unit,
    onOpenClassTimeSettings: () -> Unit,
    onExportIcs: () -> Unit,
    onSync: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf<Schedule?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 周数选择 ──
            SectionHeader(
                title = "周数",
                action = {
                    TextButton(onClick = { onWeekSelected(currentWeek) }) {
                        Text("回到本周", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(totalWeeks) { index ->
                    val week = index + 1
                    val isCurrent = week == currentWeek
                    FilterChip(
                        selected = week == selectedWeek,
                        onClick = { onWeekSelected(week) },
                        label = {
                            Text(
                                text = if (isCurrent) "本周" else "$week",
                                maxLines = 1,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CampusColors.ChipSelectedContainer,
                            selectedLabelColor = CampusColors.ChipSelectedOnContainer,
                        ),
                    )
                }
            }

            HorizontalDivider()

            // ── 多课表管理 ──
            SectionHeader(
                title = "课表管理",
                action = {
                    TextButton(onClick = onCreateSchedule) {
                        Text("新建", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(schedules) { schedule ->
                    Box(
                        modifier = Modifier.combinedClickable(
                            onClick = { onScheduleSelected(schedule.name) },
                            onLongClick = { showDeleteConfirm = schedule },
                        ),
                    ) {
                        FilterChip(
                            selected = schedule.id == activeSchedule?.id,
                            onClick = { onScheduleSelected(schedule.name) },
                            label = { Text(schedule.name, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CampusColors.ChipSelectedContainer,
                                selectedLabelColor = CampusColors.ChipSelectedOnContainer,
                            ),
                        )
                    }
                }
            }
            HorizontalDivider()

            // ── 捷径 ──
            SectionHeader(title = "捷径")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShortcutButton("上课时间", Modifier.weight(1f), onClick = onOpenClassTimeSettings)
                ShortcutButton("课表设置", Modifier.weight(1f), onClick = onOpenScheduleSettings)
                ShortcutButton("导出日历", Modifier.weight(1f), onClick = onExportIcs)
                ShortcutButton("导入/同步", Modifier.weight(1f), onClick = onSync)
            }
        }
    }

    // ── 删除确认对话框 ──
    showDeleteConfirm?.let { schedule ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除课表") },
            text = { Text("确定删除课表「${schedule.name}」？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSchedule(schedule.id)
                    showDeleteConfirm = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

@Composable
private fun ShortcutButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ────────────────────────────────────────────────
// 课程详情 BottomSheet —— 仿 WakeupSchedule CourseDetailFragment
// ────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CourseDetailSheet(
    course: Course,
    selectedWeek: Int,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        CourseDetailContent(
            course = course,
            selectedWeek = selectedWeek,
            onDelete = onDelete,
            onEdit = onEdit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 36.dp),
        )
    }
}

@Composable
private fun CourseDetailContent(
    course: Course,
    selectedWeek: Int,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val courseColor = CampusCourseColors.resolve(course.color)
    val hasClassThisWeek = course.weeks.isEmpty() || selectedWeek in course.weeks

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── 头部：课程色块 + 名称摘要 + 本周上课状态 ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(courseColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = course.name.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "周${course.day} 第${course.startSlot}-${course.endSlot}节 · ${course.location.ifBlank { "地点待定" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (hasClassThisWeek) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (hasClassThisWeek) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Text(
                    text = if (hasClassThisWeek) "本周上课" else "本周无课",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        // ── 详情卡片 ──
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                DetailRow(CampusIcons.Clock, "时间", "周${course.day} 第${course.startSlot}-${course.endSlot}节")
                DetailRow(CampusIcons.Calendar, "周次", formatWeeksText(course.weeks))
                DetailRow(CampusIcons.Place, "地点", course.location.ifBlank { "待定" })
                DetailRow(CampusIcons.Person, "教师", course.teacher.ifBlank { "待定" })
            }
        }

        // ── 操作按钮 ──
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    imageVector = CampusIcons.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("删除")
            }
            Button(
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = CampusIcons.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("编辑")
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 周次按连续区间压缩展示：[1,2,3,5] → “第 1-3, 5 周”；空列表表示每周。 */
private fun formatWeeksText(weeks: List<Int>): String {
    if (weeks.isEmpty()) return "每周"
    val sorted = weeks.sorted()
    val ranges = mutableListOf<Pair<Int, Int>>()
    var start = sorted.first()
    var prev = start
    for (week in sorted.drop(1)) {
        if (week == prev + 1) {
            prev = week
        } else {
            ranges.add(start to prev)
            start = week
            prev = week
        }
    }
    ranges.add(start to prev)
    return ranges.joinToString(", ", prefix = "第 ", postfix = " 周") { (s, e) ->
        if (s == e) "$s" else "$s-$e"
    }
}

// ────────────────────────────────────────────────
// 同时间课程选择 Dialog
// ────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CourseSelectionDialog(
    courses: List<Course>,
    selectedWeek: Int,
    onDismiss: () -> Unit,
    onCourseSelected: (Course) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "同时间课程（${courses.size}）",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            HorizontalDivider()
            courses.forEach { course ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onCourseSelected(course) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = course.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = buildString {
                                append(course.location.ifBlank { "地点待定" })
                                if (course.teacher.isNotBlank()) append("  ·  ${course.teacher}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────
// 开学日期确认弹窗
// ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDateConfirmDialog(
    currentStartDate: String,
    onConfirm: () -> Unit,
    onUpdate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val parsedDate = remember(currentStartDate) {
        runCatching { LocalDate.parse(currentStartDate) }.getOrNull()
    }
    val displayDate = parsedDate?.let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" } ?: currentStartDate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认开学日期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前课表的开学日期为：")
                Text(
                    text = displayDate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "开学日期用于计算每周的日期显示，请确认是否正确。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认正确")
            }
        },
        dismissButton = {
            TextButton(onClick = { showDatePicker = true }) {
                Text("修改日期")
            }
        },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = parsedDate?.toEpochDay()?.let { it * 86400000L },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        onUpdate(date.toString())
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ────────────────────────────────────────────────
// 辅助组件
// ────────────────────────────────────────────────

@Composable
private fun StaggeredEnterItem(
    delayMillis: Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            initialOffsetY = { -24 },
        ),
    ) {
        content()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NewScheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建课表") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("课表名称") },
                placeholder = { Text("例如：2025-2026 第一学期") },
                isError = nameError,
                supportingText = if (nameError) {{ Text("请输入名称") }} else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { nameError = true; return@TextButton }
                onConfirm(name.trim())
            }) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private class ScheduleViewModelFactory(
    private val scheduleRepository: ScheduleRepository,
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ScheduleViewModel(scheduleRepository, syncRepository, authRepository) as T
}

// ────────────────────────────────────────────────
// Preview
// ────────────────────────────────────────────────

/** 学期阶段提示条：点击可重新设置开学日期（阶段异常往往是日期不准导致的）。 */
@Composable
private fun TermPhasePrompt(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
        )
    }
}

@Preview(name = "WakeupSchedule Style")
@Composable
private fun ScheduleScreenPreview() {
    CampusTheme {
        ScheduleScreen(
            uiState = previewScheduleState,
            onSync = {},
            onScheduleSelected = {},
            onWeekSelected = {},
            onPreviousWeek = {},
            onNextWeek = {},
        )
    }
}

@Preview(name = "WakeupSchedule Style Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScheduleScreenDarkPreview() {
    CampusTheme {
        ScheduleScreen(
            uiState = previewScheduleState,
            onSync = {},
            onScheduleSelected = {},
            onWeekSelected = {},
            onPreviousWeek = {},
            onNextWeek = {},
        )
    }
}

private val previewSchedule = Schedule(
    id = "schedule-preview",
    name = "2025-2026 第二学期",
    courses = listOf(
        previewCourse(id = "1", name = "专业综合实践\n课程设计", day = 1, startSlot = 1, endSlot = 2, color = "#E96C93"),
        previewCourse(id = "2", name = "大学英语", day = 1, startSlot = 3, endSlot = 4, color = "#72A5F2"),
        previewCourse(id = "3", name = "大学英语", day = 1, startSlot = 5, endSlot = 6, color = "#72A5F2"),
        previewCourse(id = "4", name = "大学英语", day = 1, startSlot = 9, endSlot = 10, color = "#72A5F2"),
        previewCourse(id = "5", name = "线性代数", day = 2, startSlot = 3, endSlot = 4, color = "#E49AB1"),
        previewCourse(id = "6", name = "专业综合实践\n课程设计", day = 2, startSlot = 9, endSlot = 10, color = "#E96C93"),
        previewCourse(id = "7", name = "线性代数", day = 3, startSlot = 1, endSlot = 2, color = "#E49AB1"),
        previewCourse(id = "8", name = "线性代数", day = 3, startSlot = 5, endSlot = 6, color = "#E49AB1"),
        previewCourse(id = "9", name = "大学物理", day = 3, startSlot = 3, endSlot = 4, color = "#69D2C1"),
        previewCourse(id = "10", name = "专业综合实践\n课程设计", day = 4, startSlot = 3, endSlot = 4, color = "#E65C86"),
        previewCourse(id = "11", name = "大学物理", day = 5, startSlot = 3, endSlot = 4, color = "#69D2C1"),
    ),
    startDate = "2026-04-13",
    totalWeeks = 20,
    dataSources = emptyList(),
    deletedJwxtKeys = emptySet(),
)

private val previewScheduleState = ScheduleUiState(
    schedules = listOf(previewSchedule),
    activeSchedule = previewSchedule,
    isLoggedIn = true,
    selectedWeek = 3,
    currentWeek = 16,
)

private fun previewCourse(
    id: String,
    name: String,
    day: Int,
    startSlot: Int,
    endSlot: Int,
    color: String,
) = Course(
    id = id,
    name = name,
    location = if (id == "2" || id == "3" || id == "4") "教室甲" else "教室乙",
    teacher = if (id == "2" || id == "3" || id == "4") "教师甲" else "教师乙，教师丙",
    day = day,
    startSlot = startSlot,
    endSlot = endSlot,
    color = color,
    weeks = emptyList(),
    source = CourseSource.JWXT,
    jwxtKey = id,
)
