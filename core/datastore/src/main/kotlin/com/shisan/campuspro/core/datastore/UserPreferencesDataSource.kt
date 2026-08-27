package com.shisan.campuspro.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shisan.campuspro.core.model.DarkThemeConfig
import com.shisan.campuspro.core.model.NotificationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "campus_preferences")

class UserPreferencesDataSource(
    private val context: Context,
) {
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[DARK_MODE] ?: false }
    val darkThemeConfig: Flow<DarkThemeConfig> = context.dataStore.data.map { preferences ->
        preferences[DARK_THEME_CONFIG]?.let { value ->
            runCatching { DarkThemeConfig.valueOf(value) }.getOrNull()
        } ?: if (preferences[DARK_MODE] == true) {
            DarkThemeConfig.DARK
        } else {
            DarkThemeConfig.FOLLOW_SYSTEM
        }
    }
    val activeScheduleId: Flow<String?> = context.dataStore.data.map { it[ACTIVE_SCHEDULE_ID] }
    val hasJwxtSession: Flow<Boolean> = context.dataStore.data.map { it[JWXT_SESSION] ?: false }
    val lastLoginMode: Flow<String?> = context.dataStore.data.map { it[LOGIN_MODE] }
    val lastFullSyncAtMillis: Flow<Long?> = context.dataStore.data.map { it[LAST_FULL_SYNC_AT_MILLIS] }
    val lastAutoUpdateCheckAtMillis: Flow<Long?> = context.dataStore.data.map { it[LAST_AUTO_UPDATE_CHECK_AT_MILLIS] }
    val notificationSettings: Flow<NotificationSettings> = context.dataStore.data.map { preferences ->
        NotificationSettings(
            courseReminderEnabled = preferences[COURSE_REMINDER_ENABLED] ?: false,
            courseLeadMinutes = preferences[COURSE_LEAD_MINUTES] ?: 15,
            examUpdateEnabled = preferences[EXAM_UPDATE_ENABLED] ?: false,
            gradeUpdateEnabled = preferences[GRADE_UPDATE_ENABLED] ?: false,
            onboardingHandled = preferences[NOTIFICATION_ONBOARDING_HANDLED] ?: false,
            examBaselineInitialized = preferences[EXAM_BASELINE_INITIALIZED] ?: false,
            gradeBaselineInitialized = preferences[GRADE_BASELINE_INITIALIZED] ?: false,
        )
    }
    val scheduledCourseReminderIds: Flow<Set<String>> = context.dataStore.data.map {
        it[SCHEDULED_COURSE_REMINDER_IDS].orEmpty()
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setDarkThemeConfig(config: DarkThemeConfig) {
        context.dataStore.edit {
            it[DARK_THEME_CONFIG] = config.name
            it.remove(DARK_MODE)
        }
    }

    suspend fun setActiveScheduleId(id: String) {
        context.dataStore.edit { it[ACTIVE_SCHEDULE_ID] = id }
    }

    suspend fun setHasJwxtSession(enabled: Boolean) {
        context.dataStore.edit { it[JWXT_SESSION] = enabled }
    }

    suspend fun setLastFullSyncAtMillis(value: Long) {
        context.dataStore.edit { it[LAST_FULL_SYNC_AT_MILLIS] = value }
    }

    suspend fun setLastAutoUpdateCheckAtMillis(value: Long) {
        context.dataStore.edit { it[LAST_AUTO_UPDATE_CHECK_AT_MILLIS] = value }
    }

    suspend fun setLastLoginMode(mode: String?) {
        context.dataStore.edit {
            if (mode != null) it[LOGIN_MODE] = mode else it.remove(LOGIN_MODE)
        }
    }

    suspend fun updateNotificationSettings(transform: (NotificationSettings) -> NotificationSettings) {
        context.dataStore.edit { preferences ->
            val current = NotificationSettings(
                courseReminderEnabled = preferences[COURSE_REMINDER_ENABLED] ?: false,
                courseLeadMinutes = preferences[COURSE_LEAD_MINUTES] ?: 15,
                examUpdateEnabled = preferences[EXAM_UPDATE_ENABLED] ?: false,
                gradeUpdateEnabled = preferences[GRADE_UPDATE_ENABLED] ?: false,
                onboardingHandled = preferences[NOTIFICATION_ONBOARDING_HANDLED] ?: false,
                examBaselineInitialized = preferences[EXAM_BASELINE_INITIALIZED] ?: false,
                gradeBaselineInitialized = preferences[GRADE_BASELINE_INITIALIZED] ?: false,
            )
            val updated = transform(current)
            preferences[COURSE_REMINDER_ENABLED] = updated.courseReminderEnabled
            preferences[COURSE_LEAD_MINUTES] = updated.courseLeadMinutes.coerceIn(5, 30)
            preferences[EXAM_UPDATE_ENABLED] = updated.examUpdateEnabled
            preferences[GRADE_UPDATE_ENABLED] = updated.gradeUpdateEnabled
            preferences[NOTIFICATION_ONBOARDING_HANDLED] = updated.onboardingHandled
            preferences[EXAM_BASELINE_INITIALIZED] = updated.examBaselineInitialized
            preferences[GRADE_BASELINE_INITIALIZED] = updated.gradeBaselineInitialized
        }
    }

    suspend fun setScheduledCourseReminderIds(ids: Set<String>) {
        context.dataStore.edit { it[SCHEDULED_COURSE_REMINDER_IDS] = ids }
    }

    suspend fun resetNotificationBaselines() {
        updateNotificationSettings {
            it.copy(examBaselineInitialized = false, gradeBaselineInitialized = false)
        }
    }

    private companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val DARK_THEME_CONFIG = stringPreferencesKey("dark_theme_config")
        val ACTIVE_SCHEDULE_ID = stringPreferencesKey("active_schedule_id")
        val JWXT_SESSION = booleanPreferencesKey("jwxt_session")
        val LOGIN_MODE = stringPreferencesKey("login_mode")
        val LAST_FULL_SYNC_AT_MILLIS = longPreferencesKey("last_full_sync_at_millis")
        val LAST_AUTO_UPDATE_CHECK_AT_MILLIS = longPreferencesKey("last_auto_update_check_at_millis")
        val COURSE_REMINDER_ENABLED = booleanPreferencesKey("course_reminder_enabled")
        val COURSE_LEAD_MINUTES = intPreferencesKey("course_lead_minutes")
        val EXAM_UPDATE_ENABLED = booleanPreferencesKey("exam_update_enabled")
        val GRADE_UPDATE_ENABLED = booleanPreferencesKey("grade_update_enabled")
        val NOTIFICATION_ONBOARDING_HANDLED = booleanPreferencesKey("notification_onboarding_handled")
        val EXAM_BASELINE_INITIALIZED = booleanPreferencesKey("exam_baseline_initialized")
        val GRADE_BASELINE_INITIALIZED = booleanPreferencesKey("grade_baseline_initialized")
        val SCHEDULED_COURSE_REMINDER_IDS = stringSetPreferencesKey("scheduled_course_reminder_ids")
    }
}
