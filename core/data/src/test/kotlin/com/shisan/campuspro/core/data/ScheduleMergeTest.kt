package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleMergeTest {
    @Test
    fun `merge keeps manual courses and updates jwxt courses by stable key`() {
        val manual = course(id = "manual-1", name = "社团训练", source = CourseSource.MANUAL, jwxtKey = "")
        val oldJwxt = course(id = "jwxt-old", name = "高等数学", location = "旧教室", color = CourseColors.Blue, jwxtKey = "高等数学_1_1")
        val fetched = course(id = "fetched", name = "高等数学", location = "新教室", color = CourseColors.Emerald, jwxtKey = "高等数学_1_1")

        val merged = ScheduleMerger.mergeCourses(
            existing = listOf(oldJwxt, manual),
            fetched = listOf(fetched),
            deletedJwxtKeys = emptySet(),
        )

        assertEquals(listOf("高等数学", "社团训练"), merged.map { it.name })
        assertEquals("新教室", merged.first().location)
        assertEquals(CourseColors.Blue, merged.first().color)
        assertEquals("jwxt-old", merged.first().id)
    }

    @Test
    fun `merge skips deleted jwxt keys`() {
        val fetched = course(name = "大学物理", jwxtKey = "大学物理_2_3")

        val merged = ScheduleMerger.mergeCourses(
            existing = emptyList(),
            fetched = listOf(fetched),
            deletedJwxtKeys = setOf("大学物理_2_3"),
        )

        assertFalse(merged.any { it.jwxtKey == "大学物理_2_3" })
    }

    @Test
    fun `normalization gives same named jwxt courses one color and preserves manual colors`() {
        val courses = listOf(
            course(id = "a", name = " 示例  课程 ", color = CourseColors.Blue, jwxtKey = "a"),
            course(id = "b", name = "示例 课程", color = CourseColors.Pink, jwxtKey = "b"),
            course(id = "c", name = "另一课程", color = CourseColors.Blue, jwxtKey = "c"),
            course(id = "manual", name = "示例 课程", color = "#123456", source = CourseSource.MANUAL, jwxtKey = ""),
        )

        val normalized = ScheduleMerger.normalizeCourseColors(courses)

        assertEquals(normalized[0].color, normalized[1].color)
        assertTrue(normalized[0].color != normalized[2].color)
        assertEquals("#123456", normalized[3].color)
        assertEquals(normalized, ScheduleMerger.normalizeCourseColors(normalized))
    }

    @Test
    fun `fresh schedule uses all palette colors before reusing one`() {
        val courses = (1..CourseColors.all.size).map { index ->
            course(id = "$index", name = "课程$index", color = "", jwxtKey = "$index")
        }

        val colors = ScheduleMerger.normalizeCourseColors(courses).map { it.color }

        assertEquals(CourseColors.all.size, colors.distinct().size)
        assertTrue(CourseColors.all.size >= 12)
    }

    @Test
    fun `normalization replaces unsupported stored colors`() {
        val courses = listOf(
            course(id = "a", name = "课程A", color = "not-a-color", jwxtKey = "a"),
            course(id = "b", name = "课程B", color = "also-invalid", jwxtKey = "b"),
        )

        val colors = ScheduleMerger.normalizeCourseColors(courses).map { it.color }

        assertTrue(colors.all { it in CourseColors.all })
        assertEquals(2, colors.distinct().size)
    }

    @Test
    fun `merge matches course produced by old shifted weekday parser`() {
        val old = course(
            id = "old-id",
            name = "示例课程",
            location = "教室甲",
            color = CourseColors.Purple,
            jwxtKey = "jwxt_示例课程_1_3_教室甲_1-2-3",
        ).copy(day = 1, startSlot = 3, endSlot = 4)
        val corrected = course(
            id = "new-id",
            name = "示例课程",
            location = "教室甲",
            color = "",
            jwxtKey = "jwxt_示例课程_2_3_教室甲_1-2-3",
        ).copy(day = 2, startSlot = 3, endSlot = 4)

        val merged = ScheduleMerger.mergeCourses(listOf(old), listOf(corrected), emptySet())

        assertEquals("old-id", merged.single().id)
        assertEquals(CourseColors.Purple, merged.single().color)
        assertEquals(2, merged.single().day)
        assertEquals(corrected.jwxtKey, merged.single().jwxtKey)
    }

    @Test
    fun `merge respects tombstone produced by old shifted weekday parser`() {
        val corrected = course(
            name = "示例课程",
            location = "教室甲",
            color = "",
            jwxtKey = "jwxt_示例课程_2_3_教室甲_1-2-3",
        ).copy(day = 2, startSlot = 3, endSlot = 4, weeks = listOf(1, 2, 3))

        val merged = ScheduleMerger.mergeCourses(
            existing = emptyList(),
            fetched = listOf(corrected),
            deletedJwxtKeys = setOf("jwxt_示例课程_1_3_教室甲_1-2-3"),
        )

        assertTrue(merged.isEmpty())
    }

    private fun course(
        id: String = "course-id",
        name: String = "课程",
        location: String = "教室",
        color: String = CourseColors.Blue,
        source: CourseSource = CourseSource.JWXT,
        jwxtKey: String = "${name}_1_1",
    ) = Course(
        id = id,
        name = name,
        location = location,
        teacher = "教师",
        day = 1,
        startSlot = 1,
        endSlot = 2,
        color = color,
        weeks = listOf(1, 2, 3),
        source = source,
        jwxtKey = jwxtKey,
    )
}
