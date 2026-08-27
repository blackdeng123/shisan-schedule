package com.shisan.campuspro.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserContractTest {
    @Test
    fun `schedule parser reads visible course and hidden teacher from jwxt table`() {
        val html = """
            <table id="kbtable">
              <tr><th>时间</th><th>星期一</th><th>星期二</th></tr>
              <tr>
                <td>第一大节</td>
                <td>
                  <div id="A-1-1" class="kbcontent1">高等数学<br><font title="周次(节次)">1-3(周)</font><br><font title="教室">一教101</font></div>
                  <div id="A-1-2" class="kbcontent">高等数学<br><font title="老师">张老师</font></div>
                </td>
                <td>&nbsp;</td>
              </tr>
            </table>
        """.trimIndent()

        val parsed = ScheduleParser.parse(html)

        val course = parsed.courses.single()
        assertEquals("高等数学", course.name)
        assertEquals("张老师", course.teacher)
        assertEquals("一教101", course.location)
        assertEquals(1, course.day)
        assertEquals(1, course.startSlot)
        assertEquals(2, course.endSlot)
        assertEquals(listOf(1, 2, 3), course.weeks)
    }

    @Test
    fun `schedule parser splits multiple courses in same cell`() {
        val html = """
            <table id="kbtable">
              <tr><th>时间</th><th>星期一</th></tr>
              <tr>
                <td>第二大节</td>
                <td>
                  <div id="B-1-1" class="kbcontent1">大学英语<br><font title="周次(节次)">1-8(周)</font><br><font title="教室">二教201</font><br>----------------------<br>大学物理<br><font title="周次(节次)">9-16(周)</font><br><font title="教室">三教301</font></div>
                  <div id="B-1-2" class="kbcontent">大学英语<br><font title="老师">李老师</font><br>----------------------<br>大学物理<br><font title="老师">王老师</font></div>
                </td>
              </tr>
            </table>
        """.trimIndent()

        val parsed = ScheduleParser.parse(html)

        assertEquals(listOf("大学英语", "大学物理"), parsed.courses.map { it.name })
        assertEquals(listOf("李老师", "王老师"), parsed.courses.map { it.teacher })
        assertEquals(listOf(3, 3), parsed.courses.map { it.startSlot })
    }

    @Test
    fun `grade parser reads non numeric score`() {
        val html = """
            <table id="dataList">
              <tr><th>学期</th><th>课程名称</th><th>成绩</th><th>学分</th><th>绩点</th><th>课程属性</th></tr>
              <tr><td>2025-2026-1</td><td>体育</td><td>良</td><td>1.0</td><td>3.0</td><td>必修</td></tr>
            </table>
        """.trimIndent()

        val grades = GradeParser.parse(html)

        assertEquals("体育", grades.single().name)
        assertEquals("良", grades.single().scoreText)
        assertTrue(grades.single().score == null)
    }

    @Test
    fun `term selector parser marks the option selected by jwxt`() {
        val html = """
            <select name="xnxq01id">
              <option value="2024-2025-2">2024-2025-2</option>
              <option value="2025-2026-2">2025-2026-2</option>
              <option value="2026-2027-1" selected="selected">2026-2027-1</option>
            </select>
        """.trimIndent()

        val options = JwxtHtmlParser.parseTermOptions(html)

        assertEquals(listOf("2024-2025-2", "2025-2026-2", "2026-2027-1"), options.map { it.value })
        assertEquals(listOf(false, false, true), options.map { it.selected })
        assertEquals("2026-2027-1", JwxtHtmlParser.parseSelectedTermId(html))
    }

    @Test
    fun `term selector parser returns null when no option is selected`() {
        val html = """
            <select name="xnxq01id">
              <option value="2025-2026-2">2025-2026-2</option>
            </select>
        """.trimIndent()

        assertEquals(null, JwxtHtmlParser.parseSelectedTermId(html))
    }
}

