package com.shisan.campuspro.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 夹具 `schedule_real_structure.html` 保留了教务系统课表页（kbtable）的真实 DOM 结构，
 * 但课程名、教室与教师均已脱敏为「课程A-E / 教室甲-辛 / 教师A-E」占位符，
 * 不包含任何真实个人课表信息。修改夹具时请勿填回真实数据。
 */
class ScheduleRealStructureTest {
    private val html = checkNotNull(javaClass.getResource("/schedule_real_structure.html")).readText()

    @Test
    fun `parses th slot column and all thirteen course sessions`() {
        val courses = ScheduleParser.parse(html).courses

        assertEquals(13, courses.size)
        assertEquals(5, courses.map { it.name }.distinct().size)
        assertTrue(courses.any { it.name == "课程A" && it.day == 1 && it.startSlot == 1 })
        assertTrue(courses.any { it.name == "课程B" && it.day == 3 && it.startSlot == 1 })
        assertFalse(courses.any { it.name.contains("综合课程设计") })
    }

    @Test
    fun `splits multiple courses in one cell and pairs hidden teacher segments`() {
        val sameCell = ScheduleParser.parse(html).courses.filter { it.day == 1 && it.startSlot == 5 }

        assertEquals(listOf("课程D", "课程E"), sameCell.map { it.name })
        assertEquals(listOf("教师D", "教师E"), sameCell.map { it.teacher })
        assertEquals(listOf(listOf(1, 2, 4), (5..12).toList()), sameCell.map { it.weeks })
    }
}
