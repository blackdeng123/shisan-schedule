package com.shisan.campuspro.core.database

import com.shisan.campuspro.core.data.ExamsRepository
import com.shisan.campuspro.core.data.GradesRepository
import com.shisan.campuspro.core.data.ScheduleRepository
import com.shisan.campuspro.core.datastore.UserPreferencesDataSource
import com.shisan.campuspro.core.model.ClassTimeSeason
import com.shisan.campuspro.core.model.ClassTimeSlot
import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.Exam
import com.shisan.campuspro.core.model.Grade
import com.shisan.campuspro.core.model.Schedule
import com.shisan.campuspro.core.model.ScheduleDisplaySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 基于 Room 的课表仓库：聚合课表、课程、作息时间三张表为完整的 [Schedule] 模型。
 * 活跃课表 ID 存于 DataStore（[UserPreferencesDataSource]），故依赖 core/datastore。
 */
class RoomScheduleRepository(
    private val scheduleDao: ScheduleDao,
    private val preferences: UserPreferencesDataSource,
) : ScheduleRepository {
    override fun observeSchedules(): Flow<List<Schedule>> =
        combine(
            scheduleDao.observeSchedules(),
            scheduleDao.observeAllCourses(),
            scheduleDao.observeAllClassTimeSlots(),
        ) { schedules, courses, classTimeSlots ->
            val coursesBySchedule = courses.groupBy { it.scheduleId }
            val slotsBySchedule = classTimeSlots.groupBy { it.scheduleId }
            schedules.map { schedule ->
                schedule.toModel(
                    courses = coursesBySchedule[schedule.id].orEmpty(),
                    classTimeSlots = slotsBySchedule[schedule.id].orEmpty(),
                )
            }
        }

    override fun observeActiveSchedule(): Flow<Schedule?> =
        combine(observeSchedules(), preferences.activeScheduleId) { schedules, activeScheduleId ->
            schedules.firstOrNull { it.id == activeScheduleId } ?: schedules.firstOrNull()
        }

    override suspend fun selectSchedule(scheduleId: String) {
        preferences.setActiveScheduleId(scheduleId)
    }

    override suspend fun createSchedule(schedule: Schedule) {
        upsertSchedule(schedule)
        preferences.setActiveScheduleId(schedule.id)
    }

    override suspend fun deleteSchedule(scheduleId: String) {
        scheduleDao.deleteCoursesForSchedule(scheduleId)
        scheduleDao.deleteAllClassTimeSlotsForSchedule(scheduleId)
        scheduleDao.deleteSchedule(scheduleId)
        if (preferences.activeScheduleId.first() == scheduleId) {
            val nextScheduleId = observeSchedules().first().firstOrNull()?.id
            if (nextScheduleId != null) {
                preferences.setActiveScheduleId(nextScheduleId)
            }
        }
    }

    override suspend fun upsertSchedule(schedule: Schedule) {
        scheduleDao.upsertSchedules(listOf(schedule.toEntity()))
        scheduleDao.replaceCoursesForSchedule(
            scheduleId = schedule.id,
            courses = schedule.courses.map { it.toEntity(schedule.id) },
        )
        scheduleDao.replaceClassTimeSlotsForSchedule(
            scheduleId = schedule.id,
            season = ClassTimeSeason.SUMMER.name,
            slots = schedule.summerClassTimeSlots.map { it.toEntity(schedule.id, ClassTimeSeason.SUMMER) },
        )
        scheduleDao.replaceClassTimeSlotsForSchedule(
            scheduleId = schedule.id,
            season = ClassTimeSeason.WINTER.name,
            slots = schedule.winterClassTimeSlots.map { it.toEntity(schedule.id, ClassTimeSeason.WINTER) },
        )
        if (preferences.activeScheduleId.first() == null) {
            preferences.setActiveScheduleId(schedule.id)
        }
    }

    override suspend fun updateScheduleSettings(scheduleId: String, settings: ScheduleDisplaySettings) {
        val schedule = observeSchedules().first().firstOrNull { it.id == scheduleId } ?: return
        scheduleDao.upsertSchedules(listOf(schedule.copy(displaySettings = settings).toEntity()))
    }

    override suspend fun upsertCourse(scheduleId: String, course: Course) {
        scheduleDao.upsertCourses(listOf(course.toEntity(scheduleId)))
    }

    override suspend fun deleteCourse(scheduleId: String, courseId: String) {
        scheduleDao.deleteCourse(scheduleId, courseId)
    }

    override suspend fun updateCourses(scheduleId: String, courses: List<Course>) {
        scheduleDao.replaceCoursesForSchedule(
            scheduleId = scheduleId,
            courses = courses.map { it.toEntity(scheduleId) },
        )
    }

    override suspend fun replaceClassTimeSlots(
        scheduleId: String,
        season: ClassTimeSeason,
        slots: List<ClassTimeSlot>,
    ) {
        scheduleDao.replaceClassTimeSlotsForSchedule(
            scheduleId = scheduleId,
            season = season.name,
            slots = slots.map { it.toEntity(scheduleId, season) },
        )
    }

    override suspend fun selectClassTimeSeason(scheduleId: String, season: ClassTimeSeason) {
        val schedule = observeSchedules().first().firstOrNull { it.id == scheduleId } ?: return
        scheduleDao.upsertSchedules(listOf(schedule.copy(classTimeSeason = season).toEntity()))
    }

    override suspend fun clearAll() {
        scheduleDao.clearAllSchedules()
    }
}

class RoomGradesRepository(
    private val gradeDao: GradeDao,
) : GradesRepository {
    override fun observeGrades(): Flow<List<Grade>> =
        gradeDao.observeGrades().map { grades -> grades.map { it.toModel() } }

    override suspend fun replaceGrades(grades: List<Grade>) {
        gradeDao.clearGrades()
        gradeDao.upsertGrades(grades.map { it.toEntity() })
    }

    override suspend fun clearAll() {
        gradeDao.clearGrades()
    }
}

class RoomExamsRepository(
    private val examDao: ExamDao,
) : ExamsRepository {
    override fun observeExams(): Flow<List<Exam>> =
        examDao.observeExams().map { exams -> exams.map { it.toModel() } }

    override suspend fun replaceExams(exams: List<Exam>) {
        examDao.clearJwxtExams()
        examDao.upsertExams(exams.map { it.toEntity() })
    }

    override suspend fun clearAll() {
        examDao.clearAllExams()
    }
}
