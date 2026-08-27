package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.AuthState
import com.shisan.campuspro.core.model.AuthAutoLoginResult
import com.shisan.campuspro.core.model.ClassTimeSlot
import com.shisan.campuspro.core.model.ClassTimeSeason
import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.Exam
import com.shisan.campuspro.core.model.FullSyncResult
import com.shisan.campuspro.core.model.Grade
import com.shisan.campuspro.core.model.LoginMode
import com.shisan.campuspro.core.model.PortalWebSessionResult
import com.shisan.campuspro.core.model.Schedule
import com.shisan.campuspro.core.model.ScheduleDisplaySettings
import com.shisan.campuspro.core.model.SyncResult
import com.shisan.campuspro.core.model.SyncType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

interface AuthRepository {
    val authState: Flow<AuthState>
    suspend fun login(
        username: String,
        password: String,
        mode: LoginMode = LoginMode.PORTAL,
        rememberCredentials: Boolean = false,
    ): Result<Unit>
    suspend fun autoLogin(): Boolean
    suspend fun autoLoginResult(): AuthAutoLoginResult =
        if (autoLogin()) AuthAutoLoginResult.Success else AuthAutoLoginResult.MissingCredentials

    suspend fun preparePortalWebSession(forceRefresh: Boolean = false): PortalWebSessionResult =
        PortalWebSessionResult.PortalLoginRequired

    suspend fun logout()
}

interface ScheduleRepository {
    fun observeSchedules(): Flow<List<Schedule>>
    fun observeActiveSchedule(): Flow<Schedule?>
    suspend fun selectSchedule(scheduleId: String)
    suspend fun createSchedule(schedule: Schedule)
    suspend fun deleteSchedule(scheduleId: String)
    suspend fun upsertSchedule(schedule: Schedule)
    suspend fun updateScheduleSettings(scheduleId: String, settings: ScheduleDisplaySettings)
    suspend fun upsertCourse(scheduleId: String, course: Course)
    suspend fun deleteCourse(scheduleId: String, courseId: String)
    suspend fun updateCourses(scheduleId: String, courses: List<Course>)
    suspend fun replaceClassTimeSlots(scheduleId: String, season: ClassTimeSeason, slots: List<ClassTimeSlot>)
    suspend fun selectClassTimeSeason(scheduleId: String, season: ClassTimeSeason)
    /** 清空所有课表数据（多用户切换时使用） */
    suspend fun clearAll()
}

interface GradesRepository {
    fun observeGrades(): Flow<List<Grade>>
    suspend fun replaceGrades(grades: List<Grade>)
    /** 清空所有成绩数据（多用户切换时使用） */
    suspend fun clearAll()
}

interface ExamsRepository {
    fun observeExams(): Flow<List<Exam>>
    suspend fun replaceExams(exams: List<Exam>)
    /** 清空所有考试数据（多用户切换时使用） */
    suspend fun clearAll()
}

interface SyncRepository {
    suspend fun syncAll(autoSync: Boolean = false): FullSyncResult
    suspend fun syncAllSchedules(): SyncResult
    suspend fun syncCurrentSchedule(termId: String): SyncResult
    suspend fun syncByType(type: SyncType): SyncResult
}

class InMemoryAuthRepository : AuthRepository {
    private val state = MutableStateFlow(AuthState(isLoggedIn = false, studentId = "", studentName = ""))
    override val authState: Flow<AuthState> = state

    override suspend fun login(
        username: String,
        password: String,
        mode: LoginMode,
        rememberCredentials: Boolean,
    ): Result<Unit> {
        if (username.isBlank() || password.isBlank()) return Result.failure(IllegalArgumentException("请输入学号和密码"))
        state.value = AuthState(isLoggedIn = true, studentId = username, studentName = "用户", loginMode = mode)
        return Result.success(Unit)
    }

    override suspend fun autoLogin(): Boolean = state.value.isLoggedIn

    override suspend fun logout() {
        state.value = AuthState(isLoggedIn = false, studentId = "", studentName = "")
    }
}

class InMemoryScheduleRepository : ScheduleRepository {
    private val schedules = MutableStateFlow<List<Schedule>>(emptyList())
    private val activeScheduleId = MutableStateFlow<String?>(null)

    override fun observeSchedules(): Flow<List<Schedule>> = schedules

    override fun observeActiveSchedule(): Flow<Schedule?> =
        combine(schedules, activeScheduleId) { all, activeId ->
            all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
        }

    override suspend fun selectSchedule(scheduleId: String) {
        activeScheduleId.value = scheduleId
    }

    override suspend fun createSchedule(schedule: Schedule) {
        upsertSchedule(schedule)
        activeScheduleId.value = schedule.id
    }

    override suspend fun deleteSchedule(scheduleId: String) {
        schedules.update { current -> current.filterNot { it.id == scheduleId } }
        if (activeScheduleId.value == scheduleId) {
            activeScheduleId.value = schedules.value.firstOrNull()?.id
        }
    }

    override suspend fun upsertSchedule(schedule: Schedule) {
        schedules.update { current ->
            val index = current.indexOfFirst { it.id == schedule.id }
            if (index < 0) current + schedule else current.toMutableList().also { it[index] = schedule }
        }
        if (activeScheduleId.value == null) {
            activeScheduleId.value = schedule.id
        }
    }

    override suspend fun updateScheduleSettings(scheduleId: String, settings: ScheduleDisplaySettings) {
        schedules.update { current ->
            current.map { schedule ->
                if (schedule.id == scheduleId) schedule.copy(displaySettings = settings) else schedule
            }
        }
    }

    override suspend fun upsertCourse(scheduleId: String, course: Course) {
        schedules.update { current ->
            current.map { schedule ->
                if (schedule.id != scheduleId) {
                    schedule
                } else {
                    val index = schedule.courses.indexOfFirst { it.id == course.id }
                    val courses = if (index < 0) {
                        schedule.courses + course
                    } else {
                        schedule.courses.toMutableList().also { it[index] = course }
                    }
                    schedule.copy(courses = courses)
                }
            }
        }
    }

    override suspend fun deleteCourse(scheduleId: String, courseId: String) {
        schedules.update { current ->
            current.map { schedule ->
                if (schedule.id == scheduleId) {
                    schedule.copy(courses = schedule.courses.filterNot { it.id == courseId })
                } else {
                    schedule
                }
            }
        }
    }

    override suspend fun updateCourses(scheduleId: String, courses: List<Course>) {
        schedules.update { current ->
            current.map { schedule -> if (schedule.id == scheduleId) schedule.copy(courses = courses) else schedule }
        }
    }

    override suspend fun replaceClassTimeSlots(
        scheduleId: String,
        season: ClassTimeSeason,
        slots: List<ClassTimeSlot>,
    ) {
        schedules.update { current ->
            current.map { schedule ->
                if (schedule.id != scheduleId) schedule else when (season) {
                    ClassTimeSeason.SUMMER -> schedule.copy(summerClassTimeSlots = slots)
                    ClassTimeSeason.WINTER -> schedule.copy(winterClassTimeSlots = slots)
                }
            }
        }
    }

    override suspend fun selectClassTimeSeason(scheduleId: String, season: ClassTimeSeason) {
        schedules.update { current ->
            current.map { schedule ->
                if (schedule.id == scheduleId) schedule.copy(classTimeSeason = season) else schedule
            }
        }
    }

    override suspend fun clearAll() {
        schedules.value = emptyList()
        activeScheduleId.value = null
    }
}

class InMemoryGradesRepository : GradesRepository {
    private val grades = MutableStateFlow<List<Grade>>(emptyList())
    override fun observeGrades(): Flow<List<Grade>> = grades
    override suspend fun replaceGrades(grades: List<Grade>) {
        this.grades.value = grades
    }
    override suspend fun clearAll() {
        grades.value = emptyList()
    }
}

class InMemoryExamsRepository : ExamsRepository {
    private val exams = MutableStateFlow<List<Exam>>(emptyList())
    override fun observeExams(): Flow<List<Exam>> = exams
    override suspend fun replaceExams(exams: List<Exam>) {
        this.exams.value = exams.sortedBy { it.daysLeft }
    }
    override suspend fun clearAll() {
        exams.value = emptyList()
    }
}

class DefaultSyncRepository(
    private val scheduleRepository: ScheduleRepository,
    private val gradesRepository: GradesRepository,
    private val examsRepository: ExamsRepository,
) : SyncRepository {
    override suspend fun syncAll(autoSync: Boolean): FullSyncResult {
        val schedule = syncCurrentSchedule(TermPolicy.currentTermId())
        return FullSyncResult(
            schedule = schedule,
            grades = SyncResult(SyncType.GRADES, success = true, count = gradesRepository.observeGrades().first().size),
            exams = SyncResult(SyncType.EXAMS, success = true, count = examsRepository.observeExams().first().size),
        )
    }

    override suspend fun syncAllSchedules(): SyncResult =
        SyncResult(SyncType.ALL_SCHEDULES, success = true, count = 1)

    override suspend fun syncCurrentSchedule(termId: String): SyncResult {
        val active = scheduleRepository.observeActiveScheduleValue()
        if (active != null) {
            val merged = ScheduleMerger.mergeCourses(active.courses, emptyList(), active.deletedJwxtKeys)
            scheduleRepository.updateCourses(active.id, merged)
        } else {
            scheduleRepository.upsertSchedule(createEmptySchedule(termId))
        }
        return SyncResult(SyncType.SCHEDULE, success = true, count = active?.courses?.size ?: 0)
    }

    override suspend fun syncByType(type: SyncType): SyncResult {
        return when (type) {
            SyncType.SCHEDULE -> syncCurrentSchedule(TermPolicy.currentTermId())
            SyncType.GRADES -> SyncResult(SyncType.GRADES, success = true, count = gradesRepository.observeGrades().first().size)
            SyncType.EXAMS -> SyncResult(SyncType.EXAMS, success = true, count = examsRepository.observeExams().first().size)
            SyncType.ALL_SCHEDULES -> syncAllSchedules()
        }
    }
}

suspend fun ScheduleRepository.observeActiveScheduleValue(): Schedule? =
    observeActiveSchedule().first()

fun createEmptySchedule(termId: String): Schedule {
    val parts = termId.split("-")
    val yearName = parts.take(2).joinToString("-").ifBlank { termId }
    val termName = when (parts.getOrNull(2)) {
        "1" -> "第一学期"
        "2" -> "第二学期"
        else -> "学期"
    }
    return Schedule(
        id = termId,
        name = "$yearName $termName",
        courses = emptyList(),
        startDate = TermPolicy.inferStartDate(termId),
        totalWeeks = 20,
        dataSources = emptyList(),
        deletedJwxtKeys = emptySet(),
        termId = termId,
    )
}
