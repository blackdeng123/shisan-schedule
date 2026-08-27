package com.shisan.campuspro.core.database

import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import com.shisan.campuspro.core.model.Exam
import com.shisan.campuspro.core.model.ExamSource
import com.shisan.campuspro.core.model.Grade
import com.shisan.campuspro.core.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test

class CampusDatabaseMappersTest {
    @Test
    fun `schedule entity round trip preserves schedule and course fields`() {
        val schedule = Schedule(
            id = "schedule-1",
            name = "2026 春",
            startDate = "2026-03-01",
            totalWeeks = 20,
            courses = listOf(
                Course(
                    id = "course-1",
                    name = "高等数学",
                    location = "一教101",
                    teacher = "张老师",
                    day = 1,
                    startSlot = 1,
                    endSlot = 2,
                    color = "#2266EE",
                    weeks = listOf(1, 2, 4),
                    source = CourseSource.JWXT,
                    jwxtKey = "math-key",
                ),
            ),
            dataSources = emptyList(),
            deletedJwxtKeys = emptySet(),
            termId = "2025-2026-2",
            startDateConfirmed = true,
        )

        val entity = schedule.toEntity()
        val courseEntities = schedule.courses.map { it.toEntity(schedule.id) }
        val restored = entity.toModel(courseEntities)

        assertEquals(schedule.copy(dataSources = emptyList(), deletedJwxtKeys = emptySet()), restored)
    }

    @Test
    fun `grade entity round trip preserves grade fields`() {
        val grade = Grade(
            id = "grade-1",
            name = "大学英语",
            type = "必修",
            credits = 3.0,
            score = 92.0,
            scoreText = "92",
            gpa = 4.0,
            semester = "2025-2026-1",
        )

        assertEquals(grade, grade.toEntity().toModel())
    }

    @Test
    fun `exam entity round trip preserves exam fields`() {
        val exam = Exam(
            id = "exam-1",
            name = "大学物理",
            type = "期末",
            credits = 4.0,
            location = "教三402",
            date = "6月20日",
            daysLeft = 3,
            source = ExamSource.JWXT,
        )

        assertEquals(exam, exam.toEntity().toModel())
    }
}
