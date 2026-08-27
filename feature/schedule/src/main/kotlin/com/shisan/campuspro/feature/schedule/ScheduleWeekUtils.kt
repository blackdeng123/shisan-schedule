package com.shisan.campuspro.feature.schedule

import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.Schedule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal fun inferSelectedWeek(
    schedule: Schedule?,
    today: LocalDate = LocalDate.now(),
): Int {
    if (schedule == null) return 1
    val startDate = runCatching { LocalDate.parse(schedule.startDate) }.getOrNull() ?: return 1
    val rawWeek = ChronoUnit.WEEKS.between(startDate, today).toInt() + 1
    return rawWeek.coerceIn(1, schedule.totalWeeks.coerceAtLeast(1))
}

/**
 * 课表相对当前日期所处的学期阶段：新学期课表可能在开学前就已同步下来，
 * 主页需要区分“还未开学”与“学期已结束”，避免用户误以为周数计算错误。
 */
sealed interface TermPhase {
    data object InProgress : TermPhase
    data class NotStarted(val startDate: LocalDate, val daysUntilStart: Long) : TermPhase
    data object Ended : TermPhase
}

internal fun termPhase(
    schedule: Schedule?,
    today: LocalDate = LocalDate.now(),
): TermPhase {
    if (schedule == null) return TermPhase.InProgress
    val startDate = runCatching { LocalDate.parse(schedule.startDate) }.getOrNull() ?: return TermPhase.InProgress
    if (today < startDate) {
        return TermPhase.NotStarted(startDate, ChronoUnit.DAYS.between(today, startDate))
    }
    val endDate = startDate.plusWeeks(schedule.totalWeeks.coerceAtLeast(1).toLong())
    return if (today >= endDate) TermPhase.Ended else TermPhase.InProgress
}

internal fun Course.occursInWeek(week: Int): Boolean =
    weeks.isEmpty() || week in weeks

internal fun conflictGroups(courses: List<Course>): List<List<Course>> =
    courses
        .groupBy { it.day to it.startSlot }
        .values
        .filter { it.size > 1 }
        .map { group -> group.sortedBy { it.endSlot } }

internal fun selectedWeekDates(
    schedule: Schedule?,
    selectedWeek: Int,
    fallbackToday: LocalDate = LocalDate.now(),
): List<LocalDate> {
    val parsedStartDate = schedule?.startDate
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val weekStart = if (parsedStartDate != null) {
        parsedStartDate.plusWeeks((selectedWeek - 1).coerceAtLeast(0).toLong())
    } else {
        // 无起始日：以当前周为基准推算
        val currentWeekStart = fallbackToday.minusDays((fallbackToday.dayOfWeek.value - 1).toLong())
        currentWeekStart.plusWeeks((selectedWeek - 1).toLong())
    }
    return (0..6).map { weekStart.plusDays(it.toLong()) }
}
