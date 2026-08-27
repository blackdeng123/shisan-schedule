package com.shisan.campuspro.notification

import com.shisan.campuspro.core.model.Schedule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class CourseReminder(
    val id: String,
    val courseId: String,
    val courseName: String,
    val location: String,
    val triggerAt: ZonedDateTime,
)

class CourseReminderPlanner {
    fun plan(
        schedule: Schedule,
        now: ZonedDateTime,
        leadMinutes: Int,
        horizonDays: Long = 14,
    ): List<CourseReminder> {
        if (!schedule.startDateConfirmed || horizonDays <= 0) return emptyList()
        val startDate = runCatching { LocalDate.parse(schedule.startDate) }.getOrNull() ?: return emptyList()
        val slots = schedule.classTimeSlots.associateBy { it.index }
        val endDate = now.toLocalDate().plusDays(horizonDays - 1)

        return schedule.courses.flatMap { course ->
            val startTime = slots[course.startSlot]?.startTime
                ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                ?: return@flatMap emptyList()
            (0 until horizonDays).mapNotNull { offset ->
                val date = now.toLocalDate().plusDays(offset)
                if (date > endDate || date.dayOfWeek.value != course.day) return@mapNotNull null
                val week = ChronoUnit.WEEKS.between(startDate, date).toInt() + 1
                if (week !in 1..schedule.totalWeeks || (course.weeks.isNotEmpty() && week !in course.weeks)) {
                    return@mapNotNull null
                }
                val triggerAt = ZonedDateTime.of(date, startTime, now.zone).minusMinutes(leadMinutes.toLong())
                if (!triggerAt.isAfter(now)) return@mapNotNull null
                CourseReminder(
                    id = "${schedule.id}:${course.id}:$date",
                    courseId = course.id,
                    courseName = course.name,
                    location = course.location,
                    triggerAt = triggerAt,
                )
            }
        }.sortedBy { it.triggerAt }
    }
}
