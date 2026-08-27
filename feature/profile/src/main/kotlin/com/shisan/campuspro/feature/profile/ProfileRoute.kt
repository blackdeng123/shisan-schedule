package com.shisan.campuspro.feature.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shisan.campuspro.core.data.ScheduleRepository
import com.shisan.campuspro.core.data.createEmptySchedule
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.model.Schedule
import com.shisan.campuspro.core.model.AuthState
import com.shisan.campuspro.core.model.LoginMode
import com.shisan.campuspro.core.ui.SettingsListItem
import com.shisan.campuspro.core.ui.AdaptiveTwoPane
import com.shisan.campuspro.core.ui.LocalCampusAdaptiveInfo
import com.shisan.campuspro.core.ui.usesTwoPane
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ────────────────────────────────────────────────
// Route
// ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRoute(
    authState: AuthState = AuthState(false, "", ""),
    onOpenSettings: () -> Unit,
    onOpenScheduleManagement: () -> Unit,
    onOpenSecondClassroom: () -> Unit,
    onRequirePortalLogin: () -> Unit,
    onOpenCredential: () -> Unit = {},
    onRequireCredentialPortalLogin: () -> Unit = onRequirePortalLogin,
    onOpenNotifications: () -> Unit = {},
    onLogin: () -> Unit = {},
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoggedIn = authState.isLoggedIn
    var showPortalLoginDialog by remember { mutableStateOf(false) }
    var portalLoginForCredential by remember { mutableStateOf(false) }
    val onSecondClassroomClick = {
        when (secondClassroomEntryDecision(authState)) {
            SecondClassroomEntryDecision.Open -> onOpenSecondClassroom()
            SecondClassroomEntryDecision.RequirePortalLogin -> showPortalLoginDialog = true
        }
    }
    val onCredentialClick = {
        when (credentialEntryDecision(authState)) {
            CredentialEntryDecision.Open -> onOpenCredential()
            CredentialEntryDecision.RequirePortalLogin -> {
                portalLoginForCredential = true
                showPortalLoginDialog = true
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = if (LocalCampusAdaptiveInfo.current.usesTwoPane) GridCells.Fixed(2) else GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 用户头部卡片
            item(span = { GridItemSpan(maxLineSpan) }) {
                UserProfileHeader(isLoggedIn = isLoggedIn)
            }

            if (isLoggedIn) {
                // 功能列表第一组
                item {
                    ProfileSectionCard {
                        ProfileMenuItem(
                            icon = CampusIcons.Exams,
                            title = "教务系统",
                            subtitle = "已登录",
                            onClick = {},
                        )
                        ProfileMenuItem(
                            icon = CampusIcons.Exams,
                            title = "第二课堂",
                            subtitle = "校园活动与学分记录",
                            onClick = onSecondClassroomClick,
                        )
                        ProfileMenuItem(
                            icon = CampusIcons.Check,
                            title = "可信电子凭证",
                            subtitle = "成绩单、在读证明与办理进度",
                            onClick = onCredentialClick,
                        )
                        ProfileMenuItem(
                            icon = CampusIcons.Calendar,
                            title = "课表管理",
                            subtitle = "多学期课表与手动课程",
                            onClick = onOpenScheduleManagement,
                        )
                    }
                }

                // 功能列表第二组
                item {
                    ProfileSectionCard {
                        ProfileMenuItem(
                            icon = Icons.Rounded.Notifications,
                            title = "通知提醒",
                            subtitle = "课程、考试和成绩更新",
                            onClick = onOpenNotifications,
                        )
                        ProfileMenuItem(
                            icon = Icons.Rounded.Settings,
                            title = "设置",
                            subtitle = "主题、时间和隐私",
                            onClick = onOpenSettings,
                        )
                    }
                }

                // 退出登录
                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "退出登录",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                // 未登录状态
                item {
                    ProfileNotLoggedIn(onLogin = onLogin)
                }

                item {
                    ProfileSectionCard {
                        ProfileMenuItem(
                            icon = CampusIcons.Exams,
                            title = "教务系统",
                            subtitle = "未连接",
                            onClick = onLogin,
                        )
                        ProfileMenuItem(
                            icon = CampusIcons.Exams,
                            title = "第二课堂",
                            subtitle = "校园活动与学分记录",
                            onClick = onSecondClassroomClick,
                        )
                        ProfileMenuItem(
                            icon = Icons.Rounded.Settings,
                            title = "设置",
                            subtitle = "主题、时间和隐私",
                            onClick = onOpenSettings,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showPortalLoginDialog) {
        AlertDialog(
            onDismissRequest = { showPortalLoginDialog = false },
            title = { Text("需要门户登录") },
            text = { Text("此功能需要使用门户统一认证登录") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPortalLoginDialog = false
                        if (portalLoginForCredential) {
                            onRequireCredentialPortalLogin()
                        } else {
                            onRequirePortalLogin()
                        }
                        portalLoginForCredential = false
                    },
                ) {
                    Text("去登录")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPortalLoginDialog = false
                        portalLoginForCredential = false
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }
}

data class ScheduleManagementMessage(
    val id: Long,
    val text: String,
)

data class ScheduleManagementUiState(
    val schedules: List<Schedule> = emptyList(),
    val activeSchedule: Schedule? = null,
    val message: ScheduleManagementMessage? = null,
    val isLoading: Boolean = true,
)

class ScheduleManagementViewModel(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {
    private val message = MutableStateFlow<ScheduleManagementMessage?>(null)
    private var nextMessageId = 0L

    val uiState: StateFlow<ScheduleManagementUiState> = combine(
        scheduleRepository.observeSchedules(),
        scheduleRepository.observeActiveSchedule(),
        message,
    ) { schedules, activeSchedule, message ->
        ScheduleManagementUiState(
            schedules = schedules,
            activeSchedule = activeSchedule,
            message = message,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleManagementUiState())

    fun selectSchedule(scheduleId: String) {
        viewModelScope.launch { scheduleRepository.selectSchedule(scheduleId) }
    }

    fun createSchedule(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            showMessage("请输入课表名称")
            return
        }
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            scheduleRepository.createSchedule(createEmptySchedule(id).copy(name = trimmedName))
            showMessage("已创建课表")
        }
    }

    fun renameSchedule(scheduleId: String, name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            showMessage("请输入课表名称")
            return
        }
        viewModelScope.launch {
            val schedule = uiState.value.schedules.firstOrNull { it.id == scheduleId } ?: return@launch
            scheduleRepository.upsertSchedule(schedule.copy(name = trimmedName))
            showMessage("已重命名课表")
        }
    }

    fun deleteSchedule(scheduleId: String) {
        if (uiState.value.schedules.size <= 1) {
            showMessage("至少保留一个课表")
            return
        }
        viewModelScope.launch {
            scheduleRepository.deleteSchedule(scheduleId)
            showMessage("已删除课表")
        }
    }

    fun clearMessage(id: Long) {
        if (message.value?.id == id) {
            message.value = null
        }
    }

    private fun showMessage(text: String) {
        nextMessageId += 1
        message.value = ScheduleManagementMessage(nextMessageId, text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleManagementRoute(
    scheduleRepository: ScheduleRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ScheduleManagementViewModel = viewModel(
        factory = ScheduleManagementViewModelFactory(scheduleRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message?.id) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.text)
        viewModel.clearMessage(message.id)
    }

    ScheduleManagementScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSelectSchedule = viewModel::selectSchedule,
        onCreateSchedule = viewModel::createSchedule,
        onRenameSchedule = viewModel::renameSchedule,
        onDeleteSchedule = viewModel::deleteSchedule,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleManagementScreen(
    uiState: ScheduleManagementUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSelectSchedule: (String) -> Unit,
    onCreateSchedule: (String) -> Unit,
    onRenameSchedule: (String, String) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingSchedule by remember { mutableStateOf<Schedule?>(null) }
    var deletingSchedule by remember { mutableStateOf<Schedule?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("课表管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CampusIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showCreateDialog = true }) {
                        Text("新建")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            uiState.schedules.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无课表，点击右上角新建",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> AdaptiveTwoPane(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                mainPane = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uiState.schedules, key = { it.id }) { schedule ->
                            ScheduleManagementItem(
                                schedule = schedule,
                                selected = schedule.id == uiState.activeSchedule?.id,
                                onSelect = { onSelectSchedule(schedule.id) },
                                onRename = { renamingSchedule = schedule },
                                onDelete = { deletingSchedule = schedule },
                            )
                        }
                    }
                },
                supportingPane = {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("当前课表", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(uiState.activeSchedule?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                            Text("共 ${uiState.activeSchedule?.courses?.size ?: 0} 门课程")
                            Text("${uiState.activeSchedule?.totalWeeks ?: 0} 个教学周", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        }
    }

    if (showCreateDialog) {
        ScheduleNameDialog(
            title = "新建课表",
            initialName = "",
            confirmText = "创建",
            onDismiss = { showCreateDialog = false },
            onConfirm = {
                showCreateDialog = false
                onCreateSchedule(it)
            },
        )
    }

    renamingSchedule?.let { schedule ->
        ScheduleNameDialog(
            title = "重命名课表",
            initialName = schedule.name,
            confirmText = "保存",
            onDismiss = { renamingSchedule = null },
            onConfirm = {
                renamingSchedule = null
                onRenameSchedule(schedule.id, it)
            },
        )
    }

    deletingSchedule?.let { schedule ->
        AlertDialog(
            onDismissRequest = { deletingSchedule = null },
            title = { Text("删除课表") },
            text = { Text("确定删除「${schedule.name}」？课程也会一并删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingSchedule = null
                        onDeleteSchedule(schedule.id)
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSchedule = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ScheduleManagementItem(
    schedule: Schedule,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 1.dp,
        onClick = onSelect,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schedule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${schedule.courses.size} 门课程 · ${schedule.totalWeeks} 周",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (selected) {
                    Text(
                        text = "当前",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRename) {
                    Text("重命名")
                }
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ScheduleNameDialog(
    title: String,
    initialName: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var hasError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    hasError = false
                },
                label = { Text("课表名称") },
                isError = hasError,
                supportingText = if (hasError) {{ Text("请输入课表名称") }} else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        hasError = true
                    } else {
                        onConfirm(name)
                    }
                },
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private class ScheduleManagementViewModelFactory(
    private val scheduleRepository: ScheduleRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ScheduleManagementViewModel(scheduleRepository) as T
}

// ────────────────────────────────────────────────
// 用户头部卡片
// ────────────────────────────────────────────────

@Composable
private fun UserProfileHeader(isLoggedIn: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 头像占位
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Column {
                Text(
                    text = if (isLoggedIn) "同学你好" else "未登录",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isLoggedIn) "闪电课表 · 校园生活助手" else "登录教务系统同步数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ────────────────────────────────────────────────
// 未登录状态
// ────────────────────────────────────────────────

@Composable
private fun ProfileNotLoggedIn(onLogin: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "登录后可同步课表、成绩和考试数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            com.shisan.campuspro.core.designsystem.CampusButton(
                text = "去登录",
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ────────────────────────────────────────────────
// 功能分组卡片
// ────────────────────────────────────────────────

@Composable
private fun ProfileSectionCard(
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column {
            content()
        }
    }
}

// ────────────────────────────────────────────────
// 功能菜单项
// ────────────────────────────────────────────────

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    SettingsListItem(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leadingContent = {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
