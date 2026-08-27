package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleComparisonTest {
    @Test
    fun `normalizes equivalent courses from different login paths`() {
        val portal = listOf(course(name = " 高等 数学 ", teacher = "张三，李四"))
        val direct = listOf(course(name = "高等数学", teacher = "张三,李四"))

        val diff = ScheduleComparison.diff(portal, direct)

        assertTrue(diff.redactedSummary(), diff.isEmpty)
    }

    @Test
    fun `reports redacted difference counts`() {
        val diff = ScheduleComparison.diff(
            portalCourses = listOf(course(id = "portal", name = "门户课程")),
            jwxtDirectCourses = listOf(course(id = "direct", name = "直登课程")),
        )

        assertEquals(1, diff.onlyInPortal.size)
        assertEquals(1, diff.onlyInJwxtDirect.size)
    }

    private fun course(
        id: String = "course-id",
        name: String,
        teacher: String = "张三",
    ) = Course(
        id = id,
        name = name,
        location = "一教101",
        teacher = teacher,
        day = 1,
        startSlot = 1,
        endSlot = 2,
        color = "#72A5F2",
        weeks = listOf(1, 2, 3),
        source = CourseSource.JWXT,
        jwxtKey = id,
    )
}
