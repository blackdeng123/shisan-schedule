package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import com.shisan.campuspro.core.model.Schedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CachedCourseColorNormalizationTest {
    @Test
    fun `normalizes and persists cached jwxt colors on first load`() = runTest {
        val repository = InMemoryScheduleRepository()
        repository.createSchedule(
            Schedule(
                id = "schedule",
                name = "课表",
                courses = listOf(course("a", "课程A", CourseColors.Blue), course("b", "课程A", CourseColors.Pink)),
                startDate = "2026-03-02",
                totalWeeks = 20,
                dataSources = emptyList(),
                deletedJwxtKeys = emptySet(),
            ),
        )

        normalizeCachedCourseColors(repository)

        val colors = repository.observeActiveSchedule().first()!!.courses.map { it.color }
        assertEquals(listOf(CourseColors.Blue, CourseColors.Blue), colors)
        normalizeCachedCourseColors(repository)
        assertEquals(colors, repository.observeActiveSchedule().first()!!.courses.map { it.color })
    }

    private fun course(id: String, name: String, color: String) = Course(
        id = id,
        name = name,
        location = "教室",
        teacher = "教师",
        day = 1,
        startSlot = 1,
        endSlot = 2,
        color = color,
        weeks = listOf(1),
        source = CourseSource.JWXT,
        jwxtKey = id,
    )
}
