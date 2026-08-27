package com.shisan.campuspro

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.designsystem.CampusTheme
import com.shisan.campuspro.core.designsystem.shouldUseCampusDarkTheme
import com.shisan.campuspro.core.model.AuthAutoLoginResult
import com.shisan.campuspro.core.model.DarkThemeConfig
import com.shisan.campuspro.core.model.FullSyncResult
import com.shisan.campuspro.core.model.LoginMode
import com.shisan.campuspro.core.model.NotificationSettings
import com.shisan.campuspro.core.model.PrivacyConsentState
import com.shisan.campuspro.core.model.canUseOnlineFeatures
import com.shisan.campuspro.core.model.userMessage
import com.shisan.campuspro.core.ui.CampusAdaptiveProvider
import com.shisan.campuspro.core.ui.LocalCampusAdaptiveInfo
import com.shisan.campuspro.core.ui.usesNavigationRail
import com.shisan.campuspro.core.update.AppUpdateState
import com.shisan.campuspro.feature.auth.AuthRoute
import com.shisan.campuspro.feature.credential.CredentialWebViewRoute
import com.shisan.campuspro.feature.exams.ExamsRoute
import com.shisan.campuspro.feature.grades.GradesRoute
import com.shisan.campuspro.feature.profile.ProfileRoute
import com.shisan.campuspro.feature.profile.ScheduleManagementRoute
import com.shisan.campuspro.feature.profile.SecondClassroomRoute
import com.shisan.campuspro.feature.profile.shouldResumeCredential
import com.shisan.campuspro.feature.profile.shouldResumeSecondClassroom
import com.shisan.campuspro.feature.schedule.ClassTimeSettingsRoute
import com.shisan.campuspro.feature.schedule.CourseEditRoute
import com.shisan.campuspro.feature.schedule.ScheduleRoute
import com.shisan.campuspro.feature.schedule.ScheduleSettingsRoute
import com.shisan.campuspro.feature.settings.SettingsRoute
import com.shisan.campuspro.feature.settings.NotificationSettingsRoute
import com.shisan.campuspro.feature.settings.NotificationDeviceUiState
import com.shisan.campuspro.feature.settings.AboutUsRoute
import com.shisan.campuspro.feature.settings.MoreSettingsRoute
import com.shisan.campuspro.feature.settings.OpenSourceLicensesRoute
import com.shisan.campuspro.feature.settings.PersonalInformationCollectionRoute
import com.shisan.campuspro.feature.settings.PrivacyPolicyRoute
import com.shisan.campuspro.feature.settings.ThirdPartySharingRoute
import com.shisan.campuspro.feature.settings.UserAgreementRoute
import com.shisan.campuspro.feature.settings.settingsUpdateStatusText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.shisan.campuspro.notification.DESTINATION_EXAMS
import com.shisan.campuspro.notification.DESTINATION_EXTRA
import com.shisan.campuspro.notification.DESTINATION_GRADES
import com.shisan.campuspro.notification.DESTINATION_SCHEDULE
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {
    private var notificationDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationDestination = intent.getStringExtra(DESTINATION_EXTRA)
        enableEdgeToEdge()
        // 禁用导航栏对比度强制（API 26+），防止系统在透明导航栏后添加半透明背景（小白条）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setNavigationBarContrastEnforced(false)
        }
        val container = (application as CampusApplication).appContainer
        setContent {
            val darkThemeConfig by container.darkThemeConfig.collectAsStateWithLifecycle(
                initialValue = DarkThemeConfig.FOLLOW_SYSTEM,
            )
            val privacyConsentState by container.privacyConsentState.collectAsStateWithLifecycle(
                initialValue = PrivacyConsentState(),
            )
            var privacyReady by remember { mutableStateOf(false) }
            LaunchedEffect(container) {
                container.preparePrivacyState()
                privacyReady = true
            }
            val onlineFeaturesEnabled = canUseOnlineFeatures(privacyReady, privacyConsentState)
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = shouldUseCampusDarkTheme(
                systemDarkTheme = systemDarkTheme,
                darkThemeConfig = darkThemeConfig,
            )
            // 根据主题动态更新状态栏和导航栏图标颜色，确保内容和指示器始终可见
            LaunchedEffect(darkTheme) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val appearance = if (darkTheme) {
                        // 深色模式：状态栏和导航栏都用浅色图标（白色）
                        0
                    } else {
                        // 浅色模式：状态栏和导航栏都用深色图标
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    }
                    val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    window.insetsController?.setSystemBarsAppearance(appearance, mask)
                }
            }
            val scope = rememberCoroutineScope()
            CampusTheme(darkTheme = darkTheme) {
                CampusAdaptiveProvider {
                    AppUpdateHost(
                        manager = container.appUpdateManager,
                        enabled = onlineFeaturesEnabled,
                    ) {
                        CampusApp(
                            container = container,
                            darkThemeConfig = darkThemeConfig,
                            privacyReady = privacyReady,
                            privacyAccepted = onlineFeaturesEnabled,
                            onAcceptPrivacy = container::acceptPrivacy,
                            onRevokePrivacy = container::revokePrivacy,
                            onDarkThemeConfigChange = { config ->
                                scope.launch { container.setDarkThemeConfig(config) }
                            },
                            notificationDestination = notificationDestination,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationDestination = intent.getStringExtra(DESTINATION_EXTRA)
    }
}

private const val RouteAuth = "auth"
private const val RouteHome = "home"
private const val RouteSettings = "settings"
private const val RouteNotifications = "notifications"
private const val RouteMoreSettings = "more_settings"
private const val RoutePersonalInformation = "personal_information_collection"
private const val RouteThirdPartySharing = "third_party_sharing"
private const val RouteAboutUs = "about_us"
private const val RoutePrivacyPolicy = "privacy_policy"
private const val RouteUserAgreement = "user_agreement"
private const val RouteOpenSourceLicenses = "open_source_licenses"
private const val RouteScheduleManagement = "schedule_management"
private const val RouteSecondClassroom = "second_classroom"
private const val RouteCredential = "credential"
private const val ScreenTransitionMillis = 280
private const val PagerTransitionMillis = 300

private enum class TopLevelDestination(val label: String) {
    Schedule("课表"),
    Grades("成绩"),
    Exams("考试"),
    Profile("我的"),
}

private enum class ProtectedSyncAction {
    Schedule,
    Exams,
    Grades,
}

private data class SyncCommand(
    val id: Long,
    val action: ProtectedSyncAction,
)

internal data class SyncFeedback(
    val id: Long,
    val message: String,
)

internal fun consumeSyncFeedback(
    current: SyncFeedback?,
    consumedId: Long,
): SyncFeedback? = current?.takeUnless { it.id == consumedId }

@Composable
fun CampusApp(
    container: AndroidAppContainer,
    darkThemeConfig: DarkThemeConfig,
    privacyReady: Boolean,
    privacyAccepted: Boolean,
    onAcceptPrivacy: suspend () -> Unit,
    onRevokePrivacy: suspend () -> Unit,
    onDarkThemeConfigChange: (DarkThemeConfig) -> Unit,
    notificationDestination: String? = null,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val buildVariantExtension = remember { createBuildVariantExtension() }
    val notificationSettings by container.notificationSettings.collectAsStateWithLifecycle(initialValue = NotificationSettings())
    val activeSchedule by container.scheduleRepository.observeActiveSchedule().collectAsStateWithLifecycle(initialValue = null)
    val authState by container.authRepository.authState.collectAsStateWithLifecycle(
        initialValue = com.shisan.campuspro.core.model.AuthState(false, "", ""),
    )
    val lastFullSyncAtMillis by container.lastFullSyncAtMillis.collectAsStateWithLifecycle(initialValue = null)
    var notificationPermissionGranted by remember {
        mutableStateOf(
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
        )
    }
    var notificationDiagnosticsRefresh by remember { mutableStateOf(0) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionGranted = granted
        container.reconcileNotificationWork()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationDiagnosticsRefresh++
                notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                notificationPermissionGranted = notificationPermissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
                container.reconcileNotificationWork()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showNotificationOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(notificationDestination) {
        if (notificationDestination != null) {
            navController.navigate(RouteHome) {
                launchSingleTop = true
                popUpTo(RouteHome) { inclusive = false }
            }
        }
    }

    LaunchedEffect(authState.isLoggedIn, activeSchedule?.id, activeSchedule?.startDateConfirmed, notificationSettings.onboardingHandled, lastFullSyncAtMillis) {
        showNotificationOnboarding = authState.isLoggedIn &&
            lastFullSyncAtMillis != null &&
            activeSchedule?.courses?.isNotEmpty() == true &&
            activeSchedule?.startDateConfirmed == true &&
            !notificationSettings.onboardingHandled
    }
    val updateState by container.appUpdateManager.state.collectAsStateWithLifecycle()
    var pendingAction by remember { mutableStateOf<ProtectedSyncAction?>(null) }
    var pendingSecondClassroom by rememberSaveable { mutableStateOf(false) }
    var pendingCredential by rememberSaveable { mutableStateOf(false) }
    var syncCommand by remember { mutableStateOf<SyncCommand?>(null) }
    var nextSyncCommandId by remember { mutableStateOf(0L) }
    var syncFeedback by remember { mutableStateOf<SyncFeedback?>(null) }
    var nextFeedbackId by remember { mutableStateOf(0L) }
    var loginSyncJob by remember { mutableStateOf<Job?>(null) }
    var privacyNoticeDismissed by remember { mutableStateOf(false) }

    fun dispatchSync(action: ProtectedSyncAction) {
        nextSyncCommandId += 1
        syncCommand = SyncCommand(nextSyncCommandId, action)
    }

    fun showSyncFeedback(message: String) {
        nextFeedbackId += 1
        syncFeedback = SyncFeedback(nextFeedbackId, message)
    }

    fun requireAuthThen(action: ProtectedSyncAction) {
        if (!privacyAccepted) {
            pendingAction = action
            navController.navigate(RouteAuth) { launchSingleTop = true }
            return
        }
        scope.launch {
            when (val result = container.authRepository.autoLoginResult()) {
                AuthAutoLoginResult.Success -> dispatchSync(action)
                AuthAutoLoginResult.MissingCredentials -> {
                    pendingAction = action
                    navController.navigate(RouteAuth) { launchSingleTop = true }
                }
                is AuthAutoLoginResult.CredentialsRejected -> {
                    pendingAction = action
                    navController.navigate(RouteAuth) { launchSingleTop = true }
                }
                is AuthAutoLoginResult.NetworkFailure -> {
                    showSyncFeedback(result.message)
                }
            }
        }
    }

    fun syncAllAfterLogin() {
        loginSyncJob?.cancel()
        loginSyncJob = scope.launch {
            showSyncFeedback("登录成功，正在同步教务数据")
            val autoLoginResult = container.authRepository.autoLoginResult()
            if (autoLoginResult !is AuthAutoLoginResult.Success) {
                val msg = when (autoLoginResult) {
                    is AuthAutoLoginResult.CredentialsRejected -> autoLoginResult.message
                    is AuthAutoLoginResult.NetworkFailure -> autoLoginResult.message
                    else -> "登录状态已过期，请重新登录"
                }
                showSyncFeedback(msg)
                return@launch
            }
            val result = container.startupSyncCoordinator.syncNow()
            showSyncFeedback(result.loginSyncMessage())
        }
    }

    fun logoutAndShowLogin() {
        loginSyncJob?.cancel()
        loginSyncJob = null
        pendingAction = null
        pendingSecondClassroom = false
        pendingCredential = false
        syncCommand = null
        scope.launch {
            container.clearNotificationState()
            container.credentialRepository.clearSession()
            container.authRepository.logout()
            navController.navigate(RouteAuth) {
                launchSingleTop = true
            }
            showSyncFeedback("已退出登录")
        }
    }

    fun requirePortalLoginForSecondClassroom() {
        pendingSecondClassroom = true
        scope.launch {
            val currentMode = container.authRepository.authState.first().loginMode
            if (currentMode != LoginMode.PORTAL) {
                // 非 PORTAL 模式需要完整登出后重新登录
                container.authRepository.logout()
            }
            navController.navigate(RouteAuth) { launchSingleTop = true }
        }
    }

    fun requirePortalLoginForCredential() {
        pendingCredential = true
        scope.launch {
            val currentMode = container.authRepository.authState.first().loginMode
            if (currentMode != LoginMode.PORTAL) {
                // 非 PORTAL 模式需要完整登出后重新登录
                container.authRepository.logout()
            }
            navController.navigate(RouteAuth) { launchSingleTop = true }
        }
    }

    LaunchedEffect(container, privacyReady, privacyAccepted) {
        if (!privacyReady || !privacyAccepted) return@LaunchedEffect
        when (val autoLoginResult = container.authRepository.autoLoginResult()) {
            AuthAutoLoginResult.Success -> container.startupSyncCoordinator.syncIfStale()
            is AuthAutoLoginResult.NetworkFailure -> showSyncFeedback(autoLoginResult.message)
            AuthAutoLoginResult.MissingCredentials,
            is AuthAutoLoginResult.CredentialsRejected,
            -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = RouteHome,
        enterTransition = { rootEnterTransition() },
        exitTransition = { rootExitTransition() },
        popEnterTransition = { rootPopEnterTransition() },
        popExitTransition = { rootPopExitTransition() },
    ) {
        composable(RouteAuth) {
            BackHandler {
                pendingAction = null
                pendingSecondClassroom = false
                pendingCredential = false
                navController.popBackStack()
            }
            AuthRoute(
                repository = container.authRepository,
                onAcceptPrivacy = onAcceptPrivacy,
                onOpenPrivacyPolicy = { navController.navigate(RoutePrivacyPolicy) },
                onOpenUserAgreement = { navController.navigate(RouteUserAgreement) },
                onLoggedIn = { loginMode ->
                    val resumeSecondClassroom = shouldResumeSecondClassroom(
                        pending = pendingSecondClassroom,
                        loginMode = loginMode,
                    )
                    val resumeCredential = shouldResumeCredential(
                        pending = pendingCredential,
                        loginMode = loginMode,
                    )
                    val portalLoginWasRequested = pendingSecondClassroom
                    val credentialLoginWasRequested = pendingCredential
                    navController.popBackStack()
                    pendingAction = null
                    pendingSecondClassroom = false
                    pendingCredential = false
                    syncAllAfterLogin()
                    if (resumeSecondClassroom) {
                        navController.navigate(RouteSecondClassroom) { launchSingleTop = true }
                    } else if (resumeCredential) {
                        navController.navigate(RouteCredential) { launchSingleTop = true }
                    } else if (portalLoginWasRequested && loginMode == LoginMode.JWXT_DIRECT) {
                        showSyncFeedback("第二课堂需要使用门户统一认证登录")
                    } else if (
                        credentialLoginWasRequested && loginMode == LoginMode.JWXT_DIRECT
                    ) {
                        showSyncFeedback("可信电子凭证需要使用门户统一认证登录")
                    }
                },
            )
        }
        composable(RouteHome) {
                HomePagerScaffold(
                container = container,
                navController = navController,
                syncCommand = syncCommand,
                syncFeedback = syncFeedback,
                onSyncCommandConsumed = { id ->
                    if (syncCommand?.id == id) syncCommand = null
                },
                onSyncFeedbackConsumed = { id ->
                    syncFeedback = consumeSyncFeedback(syncFeedback, id)
                },
                onSyncRequested = ::requireAuthThen,
                onLogout = ::logoutAndShowLogin,
                onOpenSecondClassroom = {
                    if (privacyAccepted) {
                        navController.navigate(RouteSecondClassroom) { launchSingleTop = true }
                    } else {
                        pendingSecondClassroom = true
                        navController.navigate(RouteAuth) { launchSingleTop = true }
                    }
                },
                onRequirePortalLogin = ::requirePortalLoginForSecondClassroom,
                onRequireCredentialPortalLogin = ::requirePortalLoginForCredential,
                notificationDestination = notificationDestination,
            )
        }
        composable(RouteSecondClassroom) {
            SecondClassroomRoute(
                prepareSession = { container.authRepository.preparePortalWebSession() },
                onBack = { navController.popBackStack() },
                onRequirePortalLogin = {
                    navController.popBackStack()
                    requirePortalLoginForSecondClassroom()
                },
            )
        }
        composable(RouteCredential) {
            CredentialWebViewRoute(
                prepareSession = { container.authRepository.preparePortalWebSession() },
                onBack = { navController.popBackStack() },
                onRequirePortalLogin = {
                    navController.popBackStack()
                    requirePortalLoginForCredential()
                },
            )
        }
        composable(RouteSettings) {
            InitialRouteReveal {
                SettingsRoute(
                    darkThemeConfig = darkThemeConfig,
                    onDarkThemeConfigChange = onDarkThemeConfigChange,
                    updateStatus = settingsUpdateStatusText(
                        state = updateState,
                        manifestConfigured = BuildConfig.UPDATE_MANIFEST_URL.isNotBlank(),
                    ),
                    updateChecking = updateState is AppUpdateState.Checking,
                    onCheckForUpdates = {
                        if (!privacyAccepted) {
                            privacyNoticeDismissed = false
                        } else if (updateState !is AppUpdateState.Checking && updateState !is AppUpdateState.Downloading) {
                            scope.launch { container.appUpdateManager.checkForUpdate(manual = true) }
                        }
                    },
                    onNavigateToClassTimeSettings = {
                        navController.navigate("class_time_settings")
                    },
                    onNavigateToScheduleSettings = {
                        navController.navigate("schedule_settings")
                    },
                    onNavigateToMore = {
                        navController.navigate(RouteMoreSettings)
                    },
                    extraContent = {
                        val route = buildVariantExtension.route
                        if (route != null) buildVariantExtension.SettingsEntry { navController.navigate(route) }
                    },
                )
            }
        }
        composable(RouteMoreSettings) {
            InitialRouteReveal {
                MoreSettingsRoute(
                    onNavigateToPrivacyPolicy = {
                        navController.navigate(RoutePrivacyPolicy)
                    },
                    onNavigateToUserAgreement = {
                        navController.navigate(RouteUserAgreement)
                    },
                    onNavigateToPersonalInformation = {
                        navController.navigate(RoutePersonalInformation)
                    },
                    onNavigateToThirdPartySharing = {
                        navController.navigate(RouteThirdPartySharing)
                    },
                    onNavigateToAboutUs = {
                        navController.navigate(RouteAboutUs)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(RouteNotifications) {
            val powerManager = context.getSystemService(PowerManager::class.java)
            val batteryOptimized = !powerManager.isIgnoringBatteryOptimizations(context.packageName)
            val deviceDiagnostics = remember(
                notificationPermissionGranted,
                batteryOptimized,
                notificationDiagnosticsRefresh,
            ) {
                com.shisan.campuspro.notification.NotificationDeviceDiagnostics.capture(
                    context,
                    notificationPermissionGranted,
                    batteryOptimized,
                )
            }
            NotificationSettingsRoute(
                settings = notificationSettings,
                permissionGranted = notificationPermissionGranted,
                batteryOptimized = batteryOptimized,
                deviceInfo = NotificationDeviceUiState(
                    deviceName = "${deviceDiagnostics.advice.displayName} · ${deviceDiagnostics.model}",
                    androidApi = deviceDiagnostics.androidApi,
                    courseChannelImportance = deviceDiagnostics.courseChannelImportance,
                    academicChannelImportance = deviceDiagnostics.academicChannelImportance,
                    healthTitle = when (deviceDiagnostics.health.level) {
                        com.shisan.campuspro.notification.NotificationHealthLevel.BLOCKED -> "通知不可用"
                        com.shisan.campuspro.notification.NotificationHealthLevel.NEEDS_ATTENTION -> "需要调整"
                        com.shisan.campuspro.notification.NotificationHealthLevel.MANUAL_CHECK -> "需要人工确认"
                        com.shisan.campuspro.notification.NotificationHealthLevel.HEALTHY -> "状态正常"
                    },
                    riskSummary = deviceDiagnostics.advice.riskSummary,
                    guidance = deviceDiagnostics.advice.guidance,
                ),
                onCourseEnabledChange = { enabled -> scope.launch { container.updateNotificationSettings { it.copy(courseReminderEnabled = enabled) } } },
                onLeadMinutesChange = { minutes -> scope.launch { container.updateNotificationSettings { it.copy(courseLeadMinutes = minutes) } } },
                onExamEnabledChange = { enabled -> scope.launch { container.updateNotificationSettings { it.copy(examUpdateEnabled = enabled) } } },
                onGradeEnabledChange = { enabled -> scope.launch { container.updateNotificationSettings { it.copy(gradeUpdateEnabled = enabled) } } },
                onRequestPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onOpenNotificationSettings = {
                    com.shisan.campuspro.notification.NotificationSystemSettings.open(
                        context,
                        com.shisan.campuspro.notification.NotificationSettingsEntry.APP_NOTIFICATIONS,
                    )
                },
                onOpenAppSettings = {
                    com.shisan.campuspro.notification.NotificationSystemSettings.open(
                        context,
                        com.shisan.campuspro.notification.NotificationSettingsEntry.APP_DETAILS,
                    )
                },
                onOpenBatterySettings = {
                    com.shisan.campuspro.notification.NotificationSystemSettings.open(
                        context,
                        com.shisan.campuspro.notification.NotificationSettingsEntry.BATTERY_OPTIMIZATION,
                    )
                },
                onBack = navController::popBackStack,
            )
        }
        composable(RoutePrivacyPolicy) {
            InitialRouteReveal {
                PrivacyPolicyRoute(
                    isAccepted = privacyAccepted,
                    onAccept = { scope.launch { onAcceptPrivacy() } },
                    onRevoke = { scope.launch { onRevokePrivacy() } },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(RouteUserAgreement) {
            InitialRouteReveal {
                UserAgreementRoute(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(RoutePersonalInformation) {
            InitialRouteReveal {
                PersonalInformationCollectionRoute(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(RouteThirdPartySharing) {
            InitialRouteReveal {
                ThirdPartySharingRoute(
                    updateManifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(RouteAboutUs) {
            InitialRouteReveal {
                AboutUsRoute(
                    appVersionName = BuildConfig.VERSION_NAME,
                    onNavigateToOpenSourceLicenses = {
                        navController.navigate(RouteOpenSourceLicenses)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(RouteOpenSourceLicenses) {
            InitialRouteReveal {
                OpenSourceLicensesRoute(
                    libraryResourceId = R.raw.aboutlibraries,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(RouteScheduleManagement) {
            ScheduleManagementRoute(
                scheduleRepository = container.scheduleRepository,
                onBack = { navController.popBackStack() },
            )
        }
        composable("course_edit/{scheduleId}/{courseId}") { backStackEntry ->
            val scheduleId = backStackEntry.arguments?.getString("scheduleId") ?: return@composable
            val courseIdArg = backStackEntry.arguments?.getString("courseId") ?: "new"
            val activeScheduleFlow = container.scheduleRepository.observeActiveSchedule()
            InitialRouteReveal {
                val activeSchedule by activeScheduleFlow.collectAsStateWithLifecycle(initialValue = null)
                if (courseIdArg != "new" && activeSchedule == null) {
                    ThemedRouteLoading()
                } else {
                    val existingCourse = if (courseIdArg != "new") {
                        activeSchedule?.courses?.firstOrNull { it.id == courseIdArg }
                    } else {
                        null
                    }
                    CourseEditRoute(
                        scheduleId = scheduleId,
                        totalWeeks = activeSchedule?.totalWeeks ?: 20,
                        totalSlots = activeSchedule?.classTimeSlots?.size?.coerceAtLeast(10) ?: 10,
                        existingCourse = existingCourse,
                        onSave = { course ->
                            scope.launch {
                                container.scheduleRepository.upsertCourse(scheduleId, course)
                            }
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
        composable("schedule_settings") {
            val activeScheduleFlow = container.scheduleRepository.observeActiveSchedule()
            InitialRouteReveal {
                val activeSchedule by activeScheduleFlow.collectAsStateWithLifecycle(initialValue = null)
                activeSchedule?.let { schedule ->
                    ScheduleSettingsRoute(
                        schedule = schedule,
                        onSave = { newName, newTotalWeeks, newStartDate, _, newDisplaySettings ->
                            scope.launch {
                                container.scheduleRepository.upsertSchedule(
                                    schedule.copy(name = newName, totalWeeks = newTotalWeeks, startDate = newStartDate, startDateConfirmed = true)
                                )
                                container.scheduleRepository.updateScheduleSettings(schedule.id, newDisplaySettings)
                            }
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
        composable("class_time_settings") {
            val activeScheduleFlow = container.scheduleRepository.observeActiveSchedule()
            InitialRouteReveal {
                val activeSchedule by activeScheduleFlow.collectAsStateWithLifecycle(initialValue = null)
                val schedule = activeSchedule
                if (schedule == null) {
                    ThemedRouteLoading()
                } else {
                    ClassTimeSettingsRoute(
                        slots = schedule.classTimeSlots,
                        season = schedule.classTimeSeason,
                        maxUsedSlot = schedule.courses.maxOfOrNull { it.endSlot } ?: 0,
                        onSave = { newSlots ->
                            scope.launch {
                                container.scheduleRepository.replaceClassTimeSlots(
                                    schedule.id,
                                    schedule.classTimeSeason,
                                    newSlots,
                                )
                            }
                            navController.popBackStack()
                        },
                        onSeasonSelected = { season ->
                            scope.launch {
                                container.scheduleRepository.selectClassTimeSeason(schedule.id, season)
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
        buildVariantExtension.registerRoutes(this, navController, container)
    }

    if (showNotificationOnboarding) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("开启通知提醒？") },
            text = { Text("开启后将在上课前 15 分钟提醒，并在考试安排或成绩更新时发送摘要通知。后台同步不需要常驻运行。") },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationOnboarding = false
                    scope.launch {
                        container.updateNotificationSettings {
                            it.copy(
                                courseReminderEnabled = true,
                                examUpdateEnabled = true,
                                gradeUpdateEnabled = true,
                                onboardingHandled = true,
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text("开启通知") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNotificationOnboarding = false
                    scope.launch { container.updateNotificationSettings { it.copy(onboardingHandled = true) } }
                }) { Text("暂不开启") }
            },
        )
    }

    if (privacyReady && !privacyAccepted && !privacyNoticeDismissed) {
        PrivacyNoticeDialog(
            onAccept = { scope.launch { onAcceptPrivacy() } },
            onDecline = { privacyNoticeDismissed = true },
            onOpenPrivacyPolicy = {
                privacyNoticeDismissed = true
                navController.navigate(RoutePrivacyPolicy)
            },
            onOpenUserAgreement = {
                privacyNoticeDismissed = true
                navController.navigate(RouteUserAgreement)
            },
        )
    }
}

@Composable
private fun PrivacyNoticeDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenUserAgreement: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("隐私保护提示") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("闪电课表是非吉首大学官方的本地课表工具。登录和同步时，必要的账号与教务数据会直接传输至学校系统。")
                Text("同意前，应用不会自动登录、同步或在线检查更新；暂不同意仍可离线使用本地课表。")
                Row {
                    TextButton(onClick = onOpenPrivacyPolicy) { Text("隐私政策") }
                    TextButton(onClick = onOpenUserAgreement) { Text("用户协议") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("同意并继续") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("暂不同意") } },
    )
}

private fun FullSyncResult?.loginSyncMessage(): String {
    if (this == null) return "同步失败，请稍后重试"
    failureReason?.userMessage()?.let { return it }
    error?.let { return it }

    val results = listOfNotNull(schedule, grades, exams)
    val failed = results.firstOrNull { !it.success }
    if (failed != null) {
        return failed.failureReason?.userMessage() ?: failed.error ?: "同步失败，请稍后重试"
    }

    val courseCount = schedule?.count ?: 0
    val gradeCount = grades?.count ?: 0
    val examCount = exams?.count ?: 0
    return "同步完成：课程 $courseCount 门，成绩 $gradeCount 条，考试 $examCount 场"
}

@Composable
private fun HomePagerScaffold(
    container: AndroidAppContainer,
    navController: NavHostController,
    syncCommand: SyncCommand?,
    syncFeedback: SyncFeedback?,
    onSyncCommandConsumed: (Long) -> Unit,
    onSyncFeedbackConsumed: (Long) -> Unit,
    onSyncRequested: (ProtectedSyncAction) -> Unit,
    onLogout: () -> Unit,
    onOpenSecondClassroom: () -> Unit,
    onRequirePortalLogin: () -> Unit,
    onRequireCredentialPortalLogin: () -> Unit,
    notificationDestination: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val destinations = TopLevelDestination.entries
    val initialPage = when (notificationDestination) {
        DESTINATION_GRADES -> TopLevelDestination.Grades.ordinal
        DESTINATION_EXAMS -> TopLevelDestination.Exams.ordinal
        else -> TopLevelDestination.Schedule.ordinal
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { destinations.size })
    var pageAnimationJob by remember { mutableStateOf<Job?>(null) }
    var contentVisible by remember { mutableStateOf(false) }
    var navigationVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val adaptiveInfo = LocalCampusAdaptiveInfo.current

    LaunchedEffect(Unit) {
        contentVisible = true
        delay(110)
        navigationVisible = true
    }

    LaunchedEffect(notificationDestination) {
        val target = when (notificationDestination) {
            DESTINATION_GRADES -> TopLevelDestination.Grades.ordinal
            DESTINATION_EXAMS -> TopLevelDestination.Exams.ordinal
            DESTINATION_SCHEDULE -> TopLevelDestination.Schedule.ordinal
            else -> return@LaunchedEffect
        }
        pagerState.scrollToPage(target)
    }

    LaunchedEffect(syncFeedback?.id) {
        val feedback = syncFeedback ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(feedback.message)
        } finally {
            onSyncFeedbackConsumed(feedback.id)
        }
    }

    fun animateToPage(index: Int) {
        if (index == pagerState.currentPage && pagerState.currentPageOffsetFraction == 0f) return
        pageAnimationJob?.cancel()
        pageAnimationJob = scope.launch {
            pagerState.animateScrollToPage(
                page = index,
                animationSpec = tween(
                    durationMillis = PagerTransitionMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    BackHandler {
        if (pagerState.currentPage == TopLevelDestination.Schedule.ordinal) {
            (context as? Activity)?.finish()
        } else {
            animateToPage(TopLevelDestination.Schedule.ordinal)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    actionColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        bottomBar = {
            if (!adaptiveInfo.usesNavigationRail) {
                AnimatedVisibility(
                    visible = navigationVisible,
                    enter = fadeIn(tween(220)) + slideInVertically(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialOffsetY = { it / 2 },
                    ),
                    exit = fadeOut(tween(120)),
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        destinations.forEachIndexed { index, destination ->
                            NavigationBarItem(
                                selected = pagerState.currentPage == index,
                                onClick = { animateToPage(index) },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon(),
                                        contentDescription = destination.label,
                                    )
                                },
                                label = { Text(destination.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(220)) + slideInVertically(
                animationSpec = tween(360, easing = FastOutSlowInEasing),
                initialOffsetY = { -36 },
            ),
            exit = fadeOut(tween(120)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (adaptiveInfo.usesNavigationRail) {
                    AnimatedVisibility(
                        visible = navigationVisible,
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(120)),
                    ) {
                        NavigationRail(
                            modifier = Modifier.padding(vertical = 16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Spacer(Modifier.weight(1f))
                            destinations.forEachIndexed { index, destination ->
                                NavigationRailItem(
                                    selected = pagerState.currentPage == index,
                                    onClick = { animateToPage(index) },
                                    icon = {
                                        Icon(
                                            imageVector = destination.icon(),
                                            contentDescription = destination.label,
                                        )
                                    },
                                    label = { Text(destination.label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                )
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    userScrollEnabled = false,
                    beyondViewportPageCount = destinations.lastIndex,
                    key = { page -> destinations[page].name },
                ) { page ->
                    TopLevelPage(
                        destination = destinations[page],
                        container = container,
                        navController = navController,
                        syncCommand = syncCommand,
                        onSyncCommandConsumed = onSyncCommandConsumed,
                        onSyncRequested = onSyncRequested,
                        onLogout = onLogout,
                        onOpenSecondClassroom = onOpenSecondClassroom,
                        onRequirePortalLogin = onRequirePortalLogin,
                        onRequireCredentialPortalLogin = onRequireCredentialPortalLogin,
                        onShowMessage = { message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        },
                    )
                }
            }
        }
    }
}

private fun TopLevelDestination.icon() = when (this) {
    TopLevelDestination.Schedule -> CampusIcons.Calendar
    TopLevelDestination.Grades -> CampusIcons.Check
    TopLevelDestination.Exams -> CampusIcons.Exams
    TopLevelDestination.Profile -> CampusIcons.Person
}

@Composable
private fun TopLevelPage(
    destination: TopLevelDestination,
    container: AndroidAppContainer,
    navController: NavHostController,
    syncCommand: SyncCommand?,
    onSyncCommandConsumed: (Long) -> Unit,
    onSyncRequested: (ProtectedSyncAction) -> Unit,
    onLogout: () -> Unit,
    onOpenSecondClassroom: () -> Unit,
    onRequirePortalLogin: () -> Unit,
    onRequireCredentialPortalLogin: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val activeSchedule by container.scheduleRepository.observeActiveSchedule()
        .collectAsStateWithLifecycle(initialValue = null)
    when (destination) {
        TopLevelDestination.Schedule -> {
            ScheduleRoute(
                scheduleRepository = container.scheduleRepository,
                syncRepository = container.syncRepository,
                authRepository = container.authRepository,
                syncRequestId = syncCommand?.takeIf { it.action == ProtectedSyncAction.Schedule }?.id,
                onSyncRequestConsumed = onSyncCommandConsumed,
                onSyncRequested = { onSyncRequested(ProtectedSyncAction.Schedule) },
                onNavigateToCourseEdit = { scheduleId, courseId ->
                    navController.navigate("course_edit/$scheduleId/${courseId ?: "new"}")
                },
                onOpenScheduleSettings = {
                    navController.navigate("schedule_settings")
                },
                onOpenClassTimeSettings = {
                    navController.navigate("class_time_settings")
                },
                onShare = {
                    val schedule = activeSchedule
                    if (schedule != null) {
                        val text = buildString {
                            appendLine("📚 ${schedule.name}")
                            appendLine()
                            schedule.courses.groupBy { it.day }.forEach { (day, courses) ->
                                val dayName = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[day - 1]
                                appendLine("📅 $dayName")
                                courses.forEach { c ->
                                    appendLine("  第${c.startSlot}-${c.endSlot}节 ${c.name} @${c.location.ifBlank { "待定" }}")
                                }
                            }
                        }
                        com.shisan.campuspro.feature.schedule.shareText(context, text, "分享课表")
                    }
                },
                onExportIcs = {
                    val schedule = activeSchedule
                    if (schedule != null) {
                        val icsContent = com.shisan.campuspro.feature.schedule.generateIcsContent(
                            courses = schedule.courses,
                            startDate = java.time.LocalDate.parse(schedule.startDate),
                            classTimeSlots = schedule.classTimeSlots,
                            scheduleName = schedule.name,
                        )
                        val uri = com.shisan.campuspro.feature.schedule.writeIcsToFile(context, icsContent, "${schedule.name}.ics")
                        com.shisan.campuspro.feature.schedule.shareIcsFile(context, uri, schedule.name)
                    }
                },
                onShowMessage = onShowMessage,
            )
        }
        TopLevelDestination.Grades -> {
            GradesRoute(
                repository = container.gradesRepository,
                syncRepository = container.syncRepository,
                authRepository = container.authRepository,
                syncRequestId = syncCommand?.takeIf { it.action == ProtectedSyncAction.Grades }?.id,
                onSyncRequestConsumed = onSyncCommandConsumed,
                onSyncRequested = { onSyncRequested(ProtectedSyncAction.Grades) },
                onShowMessage = onShowMessage,
            )
        }
        TopLevelDestination.Exams -> {
            ExamsRoute(
                repository = container.examsRepository,
                syncRepository = container.syncRepository,
                authRepository = container.authRepository,
                syncRequestId = syncCommand?.takeIf { it.action == ProtectedSyncAction.Exams }?.id,
                onSyncRequestConsumed = onSyncCommandConsumed,
                onSyncRequested = { onSyncRequested(ProtectedSyncAction.Exams) },
                onShowMessage = onShowMessage,
            )
        }
        TopLevelDestination.Profile -> {
            val authState by container.authRepository.authState
                .collectAsStateWithLifecycle(initialValue = com.shisan.campuspro.core.model.AuthState(isLoggedIn = false, studentId = "", studentName = ""))
            ProfileRoute(
                authState = authState,
                onOpenSettings = { navController.navigate(RouteSettings) },
                onOpenScheduleManagement = { navController.navigate(RouteScheduleManagement) },
                onOpenNotifications = { navController.navigate(RouteNotifications) },
                onOpenSecondClassroom = onOpenSecondClassroom,
                onOpenCredential = {
                    navController.navigate(RouteCredential) { launchSingleTop = true }
                },
                onRequireCredentialPortalLogin = {
                    onRequireCredentialPortalLogin()
                },
                onRequirePortalLogin = onRequirePortalLogin,
                onLogin = { navController.navigate(RouteAuth) },
                onLogout = onLogout,
            )
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.rootEnterTransition(): EnterTransition =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(ScreenTransitionMillis),
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.rootExitTransition(): ExitTransition =
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(ScreenTransitionMillis),
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.rootPopEnterTransition(): EnterTransition =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(ScreenTransitionMillis),
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.rootPopExitTransition(): ExitTransition =
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(ScreenTransitionMillis),
    )

@Composable
private fun InitialRouteReveal(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    // 用 Surface 包裹避免 AnimatedVisibility 不可见时透出白色 Activity 背景
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(220)) + slideInVertically(
                animationSpec = tween(360, easing = FastOutSlowInEasing),
                initialOffsetY = { -28 },
            ),
            exit = fadeOut(tween(120)) + slideOutVertically(
                animationSpec = tween(160),
                targetOffsetY = { -16 },
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun ThemedRouteLoading() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}


