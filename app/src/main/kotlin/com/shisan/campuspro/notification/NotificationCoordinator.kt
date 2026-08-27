package com.shisan.campuspro.notification

import com.shisan.campuspro.core.datastore.UserPreferencesDataSource
import com.shisan.campuspro.core.model.NotificationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 通知协调器：将通知偏好读写与 WorkManager 调度任务绑定。
 *
 * 从 DI 容器中提取，容器只需持有本类的实例并委托调用。
 */
class NotificationCoordinator(
    private val applicationScope: CoroutineScope,
    private val preferences: UserPreferencesDataSource,
    private val workScheduler: NotificationWorkScheduler,
    private val courseReminderManager: CourseReminderManager,
    private val publisher: NotificationPublisher,
) {
    fun reconcile() {
        applicationScope.launch {
            val settings = preferences.notificationSettings.first()
            workScheduler.reconcile(
                courseEnabled = settings.courseReminderEnabled,
                academicEnabled = settings.examUpdateEnabled || settings.gradeUpdateEnabled,
                permissionGranted = publisher.canPostNotifications(),
            )
        }
    }

    suspend fun updateSettings(transform: (NotificationSettings) -> NotificationSettings) {
        preferences.updateNotificationSettings(transform)
        val settings = preferences.notificationSettings.first()
        workScheduler.reconcile(
            courseEnabled = settings.courseReminderEnabled,
            academicEnabled = settings.examUpdateEnabled || settings.gradeUpdateEnabled,
            permissionGranted = publisher.canPostNotifications(),
        )
    }

    suspend fun clearState() {
        workScheduler.cancelAll()
        courseReminderManager.cancelAll()
        preferences.updateNotificationSettings {
            it.copy(
                courseReminderEnabled = false,
                examUpdateEnabled = false,
                gradeUpdateEnabled = false,
                onboardingHandled = false,
                examBaselineInitialized = false,
                gradeBaselineInitialized = false,
            )
        }
    }
}
