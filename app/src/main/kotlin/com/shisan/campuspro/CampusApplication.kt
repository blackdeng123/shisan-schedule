package com.shisan.campuspro

import android.app.Application
import android.webkit.CookieManager
import com.shisan.campuspro.core.data.AuthRepository
import com.shisan.campuspro.core.data.CredentialRepository
import com.shisan.campuspro.core.data.DefaultCredentialRepository
import com.shisan.campuspro.core.data.ExamsRepository
import com.shisan.campuspro.core.data.GradesRepository
import com.shisan.campuspro.core.data.JwxtAuthRepository
import com.shisan.campuspro.core.data.JwxtSyncRepository
import com.shisan.campuspro.core.data.NetworkCredentialRemoteDataSource
import com.shisan.campuspro.core.data.NotificationPreferenceStore
import com.shisan.campuspro.core.data.ReminderAwareScheduleRepository
import com.shisan.campuspro.core.data.ScheduleRepository
import com.shisan.campuspro.core.data.StartupSyncCoordinator
import com.shisan.campuspro.core.data.SyncMetadataStore
import com.shisan.campuspro.core.data.SyncRepository
import com.shisan.campuspro.core.data.WebCookieCleaner
import com.shisan.campuspro.core.database.CampusDatabase
import com.shisan.campuspro.core.database.RoomExamsRepository
import com.shisan.campuspro.core.database.RoomGradesRepository
import com.shisan.campuspro.core.database.RoomScheduleRepository
import com.shisan.campuspro.core.datastore.EncryptedCredentialsStore
import com.shisan.campuspro.core.datastore.PrivacyConsentStore
import com.shisan.campuspro.core.datastore.UserPreferencesDataSource
import com.shisan.campuspro.core.model.Credentials
import com.shisan.campuspro.core.model.DarkThemeConfig
import com.shisan.campuspro.core.model.LoginMode
import com.shisan.campuspro.core.model.NotificationSettings
import com.shisan.campuspro.core.model.PrivacyConsentState
import com.shisan.campuspro.core.network.CredentialClient
import com.shisan.campuspro.core.network.CredentialsProvider
import com.shisan.campuspro.core.network.JwxtClient
import com.shisan.campuspro.core.network.JwxtDirectSession
import com.shisan.campuspro.core.network.PortalSession
import com.shisan.campuspro.core.network.SessionFlagStore
import com.shisan.campuspro.core.update.AndroidInstallRequestFactory
import com.shisan.campuspro.core.update.AndroidUpdatePackageVerifier
import com.shisan.campuspro.core.update.AppUpdateManager
import com.shisan.campuspro.core.update.DataStoreUpdateCheckStore
import com.shisan.campuspro.core.update.DefaultAppUpdateManager
import com.shisan.campuspro.core.update.KtorUpdateManifestSource
import com.shisan.campuspro.core.update.KtorUpdatePackageDownloader
import com.shisan.campuspro.core.update.UpdateCacheCleaner
import com.shisan.campuspro.core.update.UpdateHttpClientFactory
import com.shisan.campuspro.notification.CourseReminderManager
import com.shisan.campuspro.notification.NotificationCoordinator
import com.shisan.campuspro.notification.NotificationPublisher
import com.shisan.campuspro.notification.NotificationWorkScheduler
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class CampusApplication : Application() {
    val appContainer: AndroidAppContainer by lazy { AndroidAppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        appContainer.reconcileNotificationWork()
    }
}

/**
 * 依赖组装容器。
 *
 * 只负责创建和暴露对象图；业务逻辑已提取到各协调器
 * （[NotificationCoordinator]、[PrivacySessionManager]）和 core 层 Repository。
 */
class AndroidAppContainer(application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val preferences = UserPreferencesDataSource(application)
    private val privacyConsentStore = PrivacyConsentStore(application)
    private val database by lazy { CampusDatabase.create(application) }
    private val credentialsStore by lazy { EncryptedCredentialsStore(application) }
    private val updateDirectory = application.cacheDir.resolve("updates").also(UpdateCacheCleaner::clean)
    private val updateHttpClient = UpdateHttpClientFactory.create()
    private val jwxtClient = JwxtClient()
    private val credentialsProvider = object : CredentialsProvider {
        override suspend fun loadCredentials(): Credentials? = credentialsStore.loadCredentials()
        override suspend fun saveCredentials(credentials: Credentials) = credentialsStore.saveCredentials(credentials)
        override suspend fun clearCredentials() = credentialsStore.clearCredentials()
    }
    private val sessionFlagStore = object : SessionFlagStore {
        override suspend fun hasSession(): Boolean = preferences.hasJwxtSession.first()
        override suspend fun setHasSession(value: Boolean) = preferences.setHasJwxtSession(value)
        override suspend fun getLoginMode(): LoginMode? =
            preferences.lastLoginMode.first()?.let {
                runCatching { LoginMode.valueOf(it) }.getOrNull()
            }
        override suspend fun setLoginMode(mode: LoginMode?) =
            preferences.setLastLoginMode(mode?.name)
    }
    private val jwxtDirectSession = JwxtDirectSession(
        client = jwxtClient,
        credentialsProvider = credentialsProvider,
        sessionFlagStore = sessionFlagStore,
    )
    private val portalSession = PortalSession(
        client = jwxtClient,
        credentialsProvider = credentialsProvider,
        sessionFlagStore = sessionFlagStore,
    )

    val notificationWorkScheduler = NotificationWorkScheduler(application)
    private val roomScheduleRepository: ScheduleRepository =
        RoomScheduleRepository(database.scheduleDao(), preferences)
    val scheduleRepository: ScheduleRepository = ReminderAwareScheduleRepository(
        delegate = roomScheduleRepository,
        onChanged = notificationWorkScheduler::requestCourseRebuild,
    )
    val gradesRepository: GradesRepository = RoomGradesRepository(database.gradeDao())
    val examsRepository: ExamsRepository = RoomExamsRepository(database.examDao())
    private val notificationPublisher = NotificationPublisher(application)
    val courseReminderManager = CourseReminderManager(application, preferences, scheduleRepository)
    private val notificationPreferenceStore = object : NotificationPreferenceStore {
        override val notificationSettings: Flow<NotificationSettings> = preferences.notificationSettings
        override suspend fun updateNotificationSettings(transform: (NotificationSettings) -> NotificationSettings) =
            preferences.updateNotificationSettings(transform)
    }
    val authRepository: AuthRepository = JwxtAuthRepository(
        directSession = jwxtDirectSession,
        portalSession = portalSession,
        credentialsProvider = credentialsProvider,
        sessionFlagStore = sessionFlagStore,
        webCookieCleaner = object : WebCookieCleaner {
            override suspend fun clear() = clearWebViewCookies()
        },
    )
    val credentialRepository: CredentialRepository = DefaultCredentialRepository(
        NetworkCredentialRemoteDataSource(
            CredentialClient(portalSessionProvider = portalSession::castgc),
        ),
    )
    val syncRepository: SyncRepository = JwxtSyncRepository(
        directSession = jwxtDirectSession,
        portalSession = portalSession,
        credentialsProvider = credentialsProvider,
        sessionFlagStore = sessionFlagStore,
        scheduleRepository = scheduleRepository,
        gradesRepository = gradesRepository,
        examsRepository = examsRepository,
        notificationPreferenceStore = notificationPreferenceStore,
        academicUpdateNotifier = notificationPublisher,
        onScheduleUpdated = notificationWorkScheduler::requestCourseRebuild,
    )
    val startupSyncCoordinator: StartupSyncCoordinator = StartupSyncCoordinator(
        syncRepository = syncRepository,
        metadataStore = object : SyncMetadataStore {
            override val lastFullSyncAtMillis: Flow<Long?> = preferences.lastFullSyncAtMillis
            override suspend fun setLastFullSyncAtMillis(value: Long) = preferences.setLastFullSyncAtMillis(value)
        },
    )
    val appUpdateManager: AppUpdateManager = DefaultAppUpdateManager(
        manifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
        currentPackageName = application.packageName,
        currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
        allowLocalhostHttp = BuildConfig.DEBUG,
        autoCheckEnabled = !BuildConfig.DEBUG,
        manifestSource = KtorUpdateManifestSource(updateHttpClient),
        checkStore = DataStoreUpdateCheckStore(preferences),
        packageDownloader = KtorUpdatePackageDownloader(
            client = updateHttpClient,
            destinationDirectory = updateDirectory,
            allowLocalhostHttp = BuildConfig.DEBUG,
        ),
        packageVerifier = AndroidUpdatePackageVerifier(application),
        installRequestFactory = AndroidInstallRequestFactory(
            context = application,
            authority = "${application.packageName}.fileprovider",
        ),
    )

    val darkThemeConfig: Flow<DarkThemeConfig> = preferences.darkThemeConfig
    val privacyConsentState: Flow<PrivacyConsentState> = privacyConsentStore.state
    val notificationSettings: Flow<NotificationSettings> = preferences.notificationSettings
    val lastFullSyncAtMillis: Flow<Long?> = preferences.lastFullSyncAtMillis

    private val notificationCoordinator = NotificationCoordinator(
        applicationScope = applicationScope,
        preferences = preferences,
        workScheduler = notificationWorkScheduler,
        courseReminderManager = courseReminderManager,
        publisher = notificationPublisher,
    )
    private val privacySessionManager = PrivacySessionManager(
        privacyConsentStore = privacyConsentStore,
        authRepository = authRepository,
        notificationCoordinator = notificationCoordinator,
    )

    fun reconcileNotificationWork() = notificationCoordinator.reconcile()

    suspend fun updateNotificationSettings(transform: (NotificationSettings) -> NotificationSettings) =
        notificationCoordinator.updateSettings(transform)

    suspend fun clearNotificationState() = notificationCoordinator.clearState()

    suspend fun preparePrivacyState() = privacySessionManager.prepareState()

    suspend fun acceptPrivacy() = privacySessionManager.accept()

    suspend fun revokePrivacy() = privacySessionManager.revoke()

    suspend fun setDarkThemeConfig(config: DarkThemeConfig) = preferences.setDarkThemeConfig(config)
}

private suspend fun clearWebViewCookies() {
    val cookieManager = CookieManager.getInstance()
    withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            cookieManager.removeAllCookies {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }
    withContext(Dispatchers.IO) {
        cookieManager.flush()
    }
}
