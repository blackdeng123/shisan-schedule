package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.ClassTimeSeason
import com.shisan.campuspro.core.model.ClassTimeSlot
import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.Schedule
import com.shisan.campuspro.core.model.ScheduleDisplaySettings

/**
 * 课表写操作装饰器：任何写操作成功后触发 [onChanged]（通常用于重建课程提醒通知）。
 */
class ReminderAwareScheduleRepository(
    private val delegate: ScheduleRepository,
    private val onChanged: () -> Unit,
) : ScheduleRepository by delegate {
    override suspend fun selectSchedule(scheduleId: String) = changed { delegate.selectSchedule(scheduleId) }
    override suspend fun createSchedule(schedule: Schedule) = changed { delegate.createSchedule(schedule) }
    override suspend fun deleteSchedule(scheduleId: String) = changed { delegate.deleteSchedule(scheduleId) }
    override suspend fun upsertSchedule(schedule: Schedule) = changed { delegate.upsertSchedule(schedule) }
    override suspend fun updateScheduleSettings(scheduleId: String, settings: ScheduleDisplaySettings) =
        changed { delegate.updateScheduleSettings(scheduleId, settings) }
    override suspend fun upsertCourse(scheduleId: String, course: Course) =
        changed { delegate.upsertCourse(scheduleId, course) }
    override suspend fun deleteCourse(scheduleId: String, courseId: String) =
        changed { delegate.deleteCourse(scheduleId, courseId) }
    override suspend fun updateCourses(scheduleId: String, courses: List<Course>) =
        changed { delegate.updateCourses(scheduleId, courses) }
    override suspend fun replaceClassTimeSlots(scheduleId: String, season: ClassTimeSeason, slots: List<ClassTimeSlot>) =
        changed { delegate.replaceClassTimeSlots(scheduleId, season, slots) }
    override suspend fun selectClassTimeSeason(scheduleId: String, season: ClassTimeSeason) =
        changed { delegate.selectClassTimeSeason(scheduleId, season) }
    override suspend fun clearAll() = changed { delegate.clearAll() }

    private suspend fun changed(block: suspend () -> Unit) {
        block()
        onChanged()
    }
}
