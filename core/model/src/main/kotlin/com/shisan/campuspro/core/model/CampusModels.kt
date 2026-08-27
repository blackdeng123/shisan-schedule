package com.shisan.campuspro.core.model

import androidx.compose.runtime.Stable

@Stable
enum class CourseSource { MANUAL, JWXT }

@Stable
enum class ExamSource { MANUAL, JWXT }

@Stable
enum class DataSourceType { ACADEMIC, PORTAL, JWXT }

@Stable
enum class SyncStatus { IDLE, SYNCING, SUCCESS, FAILED }

@Stable
enum class LoginMode { PORTAL, JWXT_DIRECT }

@Stable
enum class DarkThemeConfig { FOLLOW_SYSTEM, LIGHT, DARK }

@Stable
data class NotificationSettings(
    val courseReminderEnabled: Boolean = false,
    val courseLeadMinutes: Int = 15,
    val examUpdateEnabled: Boolean = false,
    val gradeUpdateEnabled: Boolean = false,
    val onboardingHandled: Boolean = false,
    val examBaselineInitialized: Boolean = false,
    val gradeBaselineInitialized: Boolean = false,
)

@Stable
data class Course(
    val id: String,
    val name: String,
    val location: String,
    val teacher: String,
    val day: Int,
    val startSlot: Int,
    val endSlot: Int,
    val color: String,
    val weeks: List<Int>,
    val source: CourseSource,
    val jwxtKey: String,
    val conflict: Boolean = false,
)

@Stable
data class ScheduleDisplaySettings(
    val showWeekend: Boolean = true,
    val showNonCurrentWeekCourses: Boolean = false,
    val backgroundUri: String? = null,
    val courseCardAlpha: Float = 0.92f,
    val outlineEnabled: Boolean = true,
)

@Stable
data class ClassTimeSlot(
    val index: Int,
    val startTime: String,
    val endTime: String,
)

@Stable
enum class ClassTimeSeason { SUMMER, WINTER }

@Stable
data class CourseEditDraft(
    val id: String? = null,
    val name: String = "",
    val location: String = "",
    val teacher: String = "",
    val day: Int = 1,
    val startSlot: Int = 1,
    val endSlot: Int = 2,
    val color: String = "#72A5F2",
    val weeks: List<Int> = emptyList(),
)

@Stable
data class Schedule(
    val id: String,
    val name: String,
    val courses: List<Course>,
    val startDate: String,
    val totalWeeks: Int,
    val dataSources: List<DataSourceBinding>,
    val deletedJwxtKeys: Set<String>,
    val termId: String? = null,
    val startDateConfirmed: Boolean = false,
    val displaySettings: ScheduleDisplaySettings = ScheduleDisplaySettings(),
    val classTimeSeason: ClassTimeSeason = ClassTimeSeason.SUMMER,
    val summerClassTimeSlots: List<ClassTimeSlot> = DefaultClassTimeSlots,
    val winterClassTimeSlots: List<ClassTimeSlot> = DefaultWinterClassTimeSlots,
) {
    val classTimeSlots: List<ClassTimeSlot>
        get() = when (classTimeSeason) {
            ClassTimeSeason.SUMMER -> summerClassTimeSlots
            ClassTimeSeason.WINTER -> winterClassTimeSlots
        }
}

@Stable
data class Exam(
    val id: String,
    val name: String,
    val type: String,
    val credits: Double,
    val location: String,
    val date: String,
    val daysLeft: Int,
    val source: ExamSource,
)

@Stable
data class Grade(
    val id: String,
    val name: String,
    val type: String,
    val credits: Double,
    val score: Double?,
    val scoreText: String,
    val gpa: Double,
    val semester: String? = null,
)

@Stable
data class DataSourceBinding(
    val id: String,
    val type: DataSourceType,
    val enabled: Boolean,
    val studentId: String? = null,
    val url: String? = null,
    val lastSyncAt: String? = null,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val errorMessage: String? = null,
)

@Stable
enum class AuthSessionStatus {
    RESTORING,
    AUTHENTICATED,
    OFFLINE_AUTHENTICATED,
    UNAUTHENTICATED,
}

val AuthSessionStatus.canAccessRemoteData: Boolean
    get() = this == AuthSessionStatus.AUTHENTICATED

val AuthSessionStatus.canAccessCachedData: Boolean
    get() = this != AuthSessionStatus.UNAUTHENTICATED

@Stable
data class AuthState(
    val isLoggedIn: Boolean,
    val studentId: String,
    val studentName: String,
    val loginMode: LoginMode? = null,
    val sessionStatus: AuthSessionStatus = if (isLoggedIn) {
        AuthSessionStatus.AUTHENTICATED
    } else {
        AuthSessionStatus.UNAUTHENTICATED
    },
    val sessionResolved: Boolean = true,
)

sealed interface PortalWebSessionResult {
    /** 不使用 data class，避免调试输出意外包含敏感 Cookie。 */
    class Ready(val castgc: String) : PortalWebSessionResult

    data object PortalLoginRequired : PortalWebSessionResult

    data class Failure(val message: String) : PortalWebSessionResult
}

@Stable
data class Credentials(
    val username: String,
    val password: String,
    val loginMode: LoginMode = LoginMode.JWXT_DIRECT,
)

@Stable
data class PortalLoginResult(
    val success: Boolean,
    val castgc: String? = null,
    val kickedPreviousSession: Boolean = false,
    val errorMessage: String? = null,
)

@Stable
enum class SyncFailureReason {
    AuthRequired,
    NetworkTimeout,
    NetworkUnavailable,
    SessionExpired,
    CredentialsRejected,
    CaptchaRequired,
    ParseError,
    Unknown,
}

fun SyncFailureReason.userMessage(): String = when (this) {
    SyncFailureReason.AuthRequired -> "需要登录教务系统"
    SyncFailureReason.NetworkTimeout -> "网络连接超时，请检查网络后重试"
    SyncFailureReason.NetworkUnavailable -> "网络连接失败，请稍后重试"
    SyncFailureReason.SessionExpired -> "登录状态已过期，请重新登录"
    SyncFailureReason.CredentialsRejected -> "账号或密码可能已变更，请重新登录"
    SyncFailureReason.CaptchaRequired -> "教务系统需要验证码，请重新登录"
    SyncFailureReason.ParseError -> "教务系统页面结构变化，暂时无法同步"
    SyncFailureReason.Unknown -> "同步失败，请稍后重试"
}

@Stable
sealed interface AuthAutoLoginResult {
    data object Success : AuthAutoLoginResult
    data object MissingCredentials : AuthAutoLoginResult
    data class CredentialsRejected(
        val failureReason: SyncFailureReason,
        val message: String,
    ) : AuthAutoLoginResult
    data class NetworkFailure(
        val failureReason: SyncFailureReason,
        val message: String,
    ) : AuthAutoLoginResult
}

@Stable
data class ParsedGrade(
    val grades: List<Grade>,
)

@Stable
data class SyncResult(
    val type: SyncType,
    val success: Boolean,
    val count: Int,
    val error: String? = null,
    val failureReason: SyncFailureReason? = null,
)

@Stable
data class FullSyncResult(
    val schedule: SyncResult? = null,
    val grades: SyncResult? = null,
    val exams: SyncResult? = null,
    val error: String? = null,
    val failureReason: SyncFailureReason? = null,
)

@Stable
data class AllSchedulesSyncResult(
    val success: Boolean,
    val termCount: Int,
    val totalCourses: Int,
    val activeTermId: String? = null,
    val error: String? = null,
)

@Stable
enum class SyncType { SCHEDULE, GRADES, EXAMS, ALL_SCHEDULES }

val DefaultClassTimeSlots: List<ClassTimeSlot> = listOf(
    ClassTimeSlot(1, "08:00", "08:45"),
    ClassTimeSlot(2, "08:55", "09:40"),
    ClassTimeSlot(3, "10:10", "10:55"),
    ClassTimeSlot(4, "11:05", "11:50"),
    ClassTimeSlot(5, "15:00", "15:45"),
    ClassTimeSlot(6, "15:55", "16:40"),
    ClassTimeSlot(7, "16:50", "17:35"),
    ClassTimeSlot(8, "17:45", "18:30"),
    ClassTimeSlot(9, "19:30", "20:15"),
    ClassTimeSlot(10, "20:25", "21:10"),
)

val DefaultWinterClassTimeSlots: List<ClassTimeSlot> =
    DefaultClassTimeSlots.map { slot ->
        if (slot.index < 5) slot else slot.copy(
            startTime = shiftClassTime(slot.startTime, -30),
            endTime = shiftClassTime(slot.endTime, -30),
        )
    }

fun appendClassTimeSlot(slots: List<ClassTimeSlot>): List<ClassTimeSlot> {
    val last = slots.maxByOrNull { it.index } ?: return listOf(ClassTimeSlot(1, "08:00", "08:45"))
    val duration = timeToMinutes(last.endTime) - timeToMinutes(last.startTime)
    val start = timeToMinutes(last.endTime) + 10
    return slots + ClassTimeSlot(
        index = last.index + 1,
        startTime = minutesToTime(start),
        endTime = minutesToTime(start + duration.coerceAtLeast(1)),
    )
}

fun canRemoveLastClassTimeSlot(
    slots: List<ClassTimeSlot>,
    maxUsedSlot: Int,
    minimumSlots: Int = 10,
): Boolean {
    val lastIndex = slots.maxOfOrNull { it.index } ?: return false
    return slots.size > minimumSlots && lastIndex > maxUsedSlot
}

private fun shiftClassTime(value: String, minutes: Int): String =
    minutesToTime(timeToMinutes(value) + minutes)

private fun timeToMinutes(value: String): Int {
    val parts = value.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
        (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

private fun minutesToTime(value: Int): String {
    val normalized = ((value % (24 * 60)) + (24 * 60)) % (24 * 60)
    return "%02d:%02d".format(normalized / 60, normalized % 60)
}
