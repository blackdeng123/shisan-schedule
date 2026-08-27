package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import kotlinx.coroutines.flow.first

suspend fun normalizeCachedCourseColors(scheduleRepository: ScheduleRepository) {
    scheduleRepository.observeSchedules().first().forEach { schedule ->
        val normalized = ScheduleMerger.normalizeCourseColors(schedule.courses)
        if (normalized != schedule.courses) {
            scheduleRepository.updateCourses(schedule.id, normalized)
        }
    }
}

object ScheduleMerger {
    fun mergeCourses(
        existing: List<Course>,
        fetched: List<Course>,
        deletedJwxtKeys: Set<String>,
    ): List<Course> {
        val manualCourses = existing.filter { it.source == CourseSource.MANUAL }
        val existingJwxtByKey = existing
            .filter { it.source == CourseSource.JWXT }
            .associateBy { it.jwxtKey }

        val mergedJwxt = fetched
            .asSequence()
            .filterNot { fetchedCourse ->
                fetchedCourse.compatibleJwxtKeys().any { it in deletedJwxtKeys }
            }
            .map { fetchedCourse ->
                val old = fetchedCourse.compatibleJwxtKeys()
                    .firstNotNullOfOrNull(existingJwxtByKey::get)
                if (old != null) {
                    fetchedCourse.copy(id = old.id, color = old.color, conflict = false)
                } else {
                    fetchedCourse.copy(id = fetchedCourse.jwxtKey.ifBlank { fetchedCourse.id }, conflict = false)
                }
            }
            .toList()

        val allCourses = normalizeCourseColors(mergedJwxt + manualCourses)
        return markConflicts(allCourses)
    }

    fun normalizeCourseColors(courses: List<Course>): List<Course> {
        val usedColors = courses
            .filter { it.source == CourseSource.MANUAL }
            .map { it.color }
            .filter { it.isNotBlank() }
            .toMutableSet()
        val assignedJwxtColors = mutableSetOf<String>()
        val assignments = linkedMapOf<String, String>()
        var colorIndex = 0

        courses
            .filter { it.source == CourseSource.JWXT }
            .groupBy { it.normalizedCourseName() }
            .forEach { (name, sameCourse) ->
                val preserved = sameCourse
                    .asSequence()
                    .map { it.color }
                    .firstOrNull { CourseColors.isSupported(it) && it !in assignedJwxtColors }
                val color = preserved ?: nextColor(usedColors, colorIndex)
                assignments[name] = color
                assignedJwxtColors += color
                usedColors += color
                colorIndex += 1
            }

        return courses.map { course ->
            if (course.source == CourseSource.JWXT) {
                course.copy(color = assignments.getValue(course.normalizedCourseName()))
            } else {
                course
            }
        }
    }

    /**
     * 扫描课程列表，标记同一时间段有多个课程的冲突。
     * 冲突条件：同一天、节次范围重叠、周次有交集。
     */
    private fun markConflicts(courses: List<Course>): List<Course> {
        if (courses.size < 2) return courses
        val conflictFlags = BooleanArray(courses.size)
        for (i in courses.indices) {
            if (conflictFlags[i]) continue
            val a = courses[i]
            for (j in i + 1 until courses.size) {
                val b = courses[j]
                if (isOverlapping(a, b)) {
                    conflictFlags[i] = true
                    conflictFlags[j] = true
                }
            }
        }
        return courses.mapIndexed { index, course ->
            if (conflictFlags[index]) course.copy(conflict = true) else course
        }
    }

    /** 判断两门课是否在时间和周次上重叠 */
    private fun isOverlapping(a: Course, b: Course): Boolean {
        if (a.day != b.day) return false
        // 节次范围有交集
        val slotOverlap = a.startSlot <= b.endSlot && b.startSlot <= a.endSlot
        if (!slotOverlap) return false
        // 周次有交集（空列表 = 所有周都上课）
        val weekOverlap = when {
            a.weeks.isEmpty() || b.weeks.isEmpty() -> true
            else -> a.weeks.any { it in b.weeks }
        }
        return weekOverlap
    }

    private fun nextColor(usedColors: Set<String>, startIndex: Int): String {
        for (offset in CourseColors.all.indices) {
            val color = CourseColors.all[(startIndex + offset) % CourseColors.all.size]
            if (color !in usedColors) return color
        }
        return CourseColors.all[startIndex % CourseColors.all.size]
    }

    private fun Course.normalizedCourseName(): String =
        name.trim().replace(Regex("\\s+"), " ")

    private fun Course.compatibleJwxtKeys(): List<String> = buildList {
        add(jwxtKey)
        if (day > 1) {
            add(
                listOf(
                    "jwxt",
                    name.trim(),
                    (day - 1).toString(),
                    startSlot.toString(),
                    location.trim(),
                    weeks.joinToString("-"),
                ).joinToString("_"),
            )
        }
    }
}
