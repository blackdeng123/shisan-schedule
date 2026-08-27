package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.AcademicUpdateDiff
import com.shisan.campuspro.core.model.NotificationSettings
import kotlinx.coroutines.flow.Flow

/**
 * 同步协调依赖的通知偏好存储。
 *
 * 抽象掉 core/datastore 的 Android DataStore 细节，让 core/data 保持纯 Kotlin。
 * 由 app 层适配 [com.shisan.campuspro.core.datastore.UserPreferencesDataSource]。
 */
interface NotificationPreferenceStore {
    val notificationSettings: Flow<NotificationSettings>
    suspend fun updateNotificationSettings(transform: (NotificationSettings) -> NotificationSettings)
}

/**
 * 同步完成后发布教务更新通知的端口。
 *
 * 由 app 层实现（Android 通知 API）。core/data 只依赖此端口而非具体通知实现，
 * 保证同步逻辑可独立测试。
 */
interface AcademicUpdateNotifier {
    fun publishGradeUpdates(diff: AcademicUpdateDiff)
    fun publishExamUpdates(diff: AcademicUpdateDiff)
    fun canPostNotifications(): Boolean
}

/**
 * 登出后清理 WebView Cookie 的端口。
 *
 * 由 app 层实现（android.webkit.CookieManager）。core/data 不直接触碰 Android WebView。
 */
fun interface WebCookieCleaner {
    suspend fun clear()
}
