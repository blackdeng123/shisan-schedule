package com.shisan.campuspro.feature.schedule

import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import com.shisan.campuspro.core.model.Schedule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleWeekUtilsTest {
    @Test
    fun `initial week is inferred from schedule start date`() {
        val schedule = schedule(startDate = "2026-03-09", totalWeeks = 20)

        val week = inferSelectedWeek(schedule, today = LocalDate.of(2026, 3, 23))

        assertEquals(3, week)
    }

    @Test
    fun `initial week falls back to first week when start date is blank or invalid`() {
        assertEquals(1, inferSelectedWeek(schedule(startDate = ""), today = LocalDate.of(2026, 3, 23)))
        assertEquals(1, inferSelectedWeek(schedule(startDate = "not-a-date"), today = LocalDate.of(2026, 3, 23)))
    }

    @Test
    fun `initial week is clamped to schedule total weeks`() {
        val schedule = schedule(startDate = "2026-03-09", totalWeeks = 4)

        val week = inferSelectedWeek(schedule, today = LocalDate.of(2026, 5, 1))

        assertEquals(4, week)
    }

    @Test
    fun `initial week is clamped to first week before schedule starts`() {
        val schedule = schedule(startDate = "2026-03-09", totalWeeks = 20)

        val week = inferSelectedWeek(schedule, today = LocalDate.of(2026, 3, 1))

        assertEquals(1, week)
    }

    @Test
    fun `course without week list is visible every week`() {
        assertTrue(course(weeks = emptyList()).occursInWeek(1))
        assertTrue(course(weeks = emptyList()).occursInWeek(18))
    }

    @Test
    fun `course with week list is visible only in matching week`() {
        val course = course(weeks = listOf(1, 3, 5))

        assertTrue(course.occursInWeek(3))
        assertFalse(course.occursInWeek(4))
    }

    @Test
    fun `selected week dates are derived from schedule start date`() {
        val schedule = schedule(startDate = "2026-03-09", totalWeeks = 20)

        val dates = selectedWeekDates(
            schedule = schedule,
            selectedWeek = 3,
            fallbackToday = LocalDate.of(2026, 6, 29),
        )

        assertEquals(
            listOf("3/23", "3/24", "3/25", "3/26", "3/27", "3/28", "3/29"),
            dates.map { "${it.monthValue}/${it.dayOfMonth}" },
        )
    }

    @Test
    fun `selected week dates fall back to current monday when start date is invalid`() {
        val schedule = schedule(startDate = "bad-date", totalWeeks = 20)

        val dates = selectedWeekDates(
            schedule = schedule,
            selectedWeek = 1,
            fallbackToday = LocalDate.of(2026, 6, 29),
        )

        assertEquals(
            listOf("6/29", "6/30", "7/1", "7/2", "7/3", "7/4", "7/5"),
            dates.map { "${it.monthValue}/${it.dayOfMonth}" },
        )
    }

    @Test
    fun `selected week dates offset from current week when start date is invalid`() {
        val schedule = schedule(startDate = "bad-date", totalWeeks = 20)

        val dates = selectedWeekDates(
            schedule = schedule,
            selectedWeek = 3,
            fallbackToday = LocalDate.of(2026, 6, 29),
        )

        assertEquals(
            listOf("7/13", "7/14", "7/15", "7/16", "7/17", "7/18", "7/19"),
            dates.map { "${it.monthValue}/${it.dayOfMonth}" },
        )
    }

    @Test
    fun `conflict groups include courses sharing day and start slot`() {
        val first = course(id = "a", weeks = emptyList(), day = 2, startSlot = 3)
        val second = course(id = "b", weeks = emptyList(), day = 2, startSlot = 3)
        val separate = course(id = "c", weeks = emptyList(), day = 2, startSlot = 5)

        val groups = conflictGroups(listOf(first, second, separate))

        assertEquals(1, groups.size)
        assertEquals(listOf("a", "b"), groups.first().map { it.id })
    }

    @Test
    fun `term phase is not started before schedule start date`() {
        val schedule = schedule(startDate = "2026-09-07", totalWeeks = 20)

        val phase = termPhase(schedule, today = LocalDate.of(2026, 8, 27))

        assertTrue(phase is TermPhase.NotStarted)
        phase as TermPhase.NotStarted
        assertEquals(LocalDate.of(2026, 9, 7), phase.startDate)
        assertEquals(11L, phase.daysUntilStart)
    }

    @Test
    fun `term phase is in progress between start date and term end`() {
        val schedule = schedule(startDate = "2026-03-09", totalWeeks = 20)

        // 开学第一天与最后一周的任意一天都算进行中
        assertEquals(TermPhase.InProgress, termPhase(schedule, today = LocalDate.of(2026, 3, 9)))
        assertEquals(TermPhase.InProgress, termPhase(schedule, today = LocalDate.of(2026, 7, 26)))
    }

    @Test
    fun `term phase is ended after last week`() {
        // 2026-03-09 开学，20 周 → 最后一周结束于 2026-07-26（含）
        val schedule = schedule(startDate = "2026-03-09", totalWeeks = 20)

        assertEquals(TermPhase.Ended, termPhase(schedule, today = LocalDate.of(2026, 7, 27)))
        assertEquals(TermPhase.Ended, termPhase(schedule, today = LocalDate.of(2026, 8, 27)))
    }

    @Test
    fun `term phase falls back to in progress when start date is missing`() {
        assertEquals(TermPhase.InProgress, termPhase(schedule(startDate = ""), today = LocalDate.of(2026, 8, 27)))
        assertEquals(TermPhase.InProgress, termPhase(schedule(startDate = "bad-date"), today = LocalDate.of(2026, 8, 27)))
        assertEquals(TermPhase.InProgress, termPhase(null, today = LocalDate.of(2026, 8, 27)))
    }

    private fun schedule(
        startDate: String,
        totalWeeks: Int = 20,
    ) = Schedule(
        id = "schedule-id",
        name = "2025-2026 第二学期",
        courses = emptyList(),
        startDate = startDate,
        totalWeeks = totalWeeks,
        dataSources = emptyList(),
        deletedJwxtKeys = emptySet(),
    )

    private fun course(
        weeks: List<Int>,
        id: String = "course-id",
        day: Int = 1,
        startSlot: Int = 1,
    ) = Course(
        id = id,
        name = "高等数学",
        location = "A101",
        teacher = "张老师",
        day = day,
        startSlot = startSlot,
        endSlot = startSlot + 1,
        color = "#3B82F6",
        weeks = weeks,
        source = CourseSource.JWXT,
        jwxtKey = "高等数学_1_1",
    )
}
