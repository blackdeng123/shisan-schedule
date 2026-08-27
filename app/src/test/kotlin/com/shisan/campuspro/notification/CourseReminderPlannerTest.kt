package com.shisan.campuspro.notification

import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import com.shisan.campuspro.core.model.Schedule
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `plans matching course inside fourteen day window`() {
        val now = ZonedDateTime.of(2026, 3, 9, 7, 0, 0, 0, zone)

        val reminders = CourseReminderPlanner().plan(
            schedule = schedule(weeks = listOf(1, 2)),
            now = now,
            leadMinutes = 15,
        )

        assertEquals(2, reminders.size)
        assertEquals("2026-03-09T07:45+08:00[Asia/Shanghai]", reminders.first().triggerAt.toString())
        assertEquals("高等数学", reminders.first().courseName)
    }

    @Test
    fun `does not plan before start date is confirmed`() {
        val reminders = CourseReminderPlanner().plan(
            schedule = schedule(weeks = listOf(1), startDateConfirmed = false),
            now = ZonedDateTime.of(2026, 3, 9, 7, 0, 0, 0, zone),
            leadMinutes = 15,
        )

        assertTrue(reminders.isEmpty())
    }

    @Test
    fun `skips past invalid slot and non matching weeks`() {
        val now = ZonedDateTime.of(2026, 3, 9, 9, 0, 0, 0, zone)
        val invalidSlot = schedule(weeks = listOf(1)).copy(
            courses = listOf(course(weeks = listOf(1), startSlot = 99)),
        )

        assertTrue(CourseReminderPlanner().plan(schedule(weeks = listOf(1)), now, 15).isEmpty())
        assertTrue(CourseReminderPlanner().plan(invalidSlot, now, 15).isEmpty())
        assertTrue(CourseReminderPlanner().plan(schedule(weeks = listOf(3)), now, 15).isEmpty())
    }

    private fun schedule(
        weeks: List<Int>,
        startDateConfirmed: Boolean = true,
    ) = Schedule(
        id = "schedule",
        name = "第二学期",
        courses = listOf(course(weeks)),
        startDate = "2026-03-09",
        totalWeeks = 20,
        dataSources = emptyList(),
        deletedJwxtKeys = emptySet(),
        startDateConfirmed = startDateConfirmed,
    )

    private fun course(
        weeks: List<Int>,
        startSlot: Int = 1,
    ) = Course(
        id = "course",
        name = "高等数学",
        location = "A101",
        teacher = "张老师",
        day = 1,
        startSlot = startSlot,
        endSlot = startSlot + 1,
        color = "#3B82F6",
        weeks = weeks,
        source = CourseSource.JWXT,
        jwxtKey = "math",
    )
}
