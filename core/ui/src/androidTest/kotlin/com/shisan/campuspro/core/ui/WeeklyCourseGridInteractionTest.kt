package com.shisan.campuspro.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WeeklyCourseGridInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun blankAreaClickIsSeparateFromCourseClick() {
        var emptyClicks = 0
        var courseClicks = 0
        composeRule.setContent {
            WeeklyCourseGrid(
                courses = listOf(course()),
                selectedWeek = 1,
                totalSlots = 2,
                onEmptyAreaClick = { emptyClicks += 1 },
                onCoursesClick = { courseClicks += 1 },
            )
        }

        composeRule.onNodeWithTag("schedule-day-2").performClick()
        composeRule.runOnIdle {
            assertEquals(1, emptyClicks)
            assertEquals(0, courseClicks)
        }

        composeRule.onNodeWithTag("course-course-1").performClick()
        composeRule.runOnIdle {
            assertEquals(1, emptyClicks)
            assertEquals(1, courseClicks)
        }
    }

    private fun course() = Course(
        id = "course-1",
        name = "高等数学",
        location = "一教101",
        teacher = "张老师",
        day = 1,
        startSlot = 1,
        endSlot = 2,
        color = "blue",
        weeks = listOf(1),
        source = CourseSource.JWXT,
        jwxtKey = "course-1",
    )
}
