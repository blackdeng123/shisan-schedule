package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.CourseSource
import com.shisan.campuspro.core.model.ExamSource
import org.junit.Assert.assertEquals
import org.junit.Test

class JwxtParserTest {
    @Test
    fun `parse grades reads table headers by text`() {
        val html = """
            <table id="dataList">
              <tr><th>课程名称</th><th>课程属性</th><th>学分</th><th>成绩</th><th>绩点</th></tr>
              <tr><td>高等数学</td><td>必修</td><td>5.0</td><td>92</td><td>4.2</td></tr>
            </table>
        """.trimIndent()

        val grades = JwxtHtmlParser.parseGrades(html)

        assertEquals("高等数学", grades.single().name)
        assertEquals(92.0, grades.single().score)
        assertEquals(4.2, grades.single().gpa, 0.0)
    }

    @Test
    fun `parse grades falls back to score formula when gpa column missing`() {
        // 教务页面缺少绩点列时，按 (成绩-60)/10+1 兜底，等级制按优=4.5/良=3.5，
        // 避免绩点全为 0 拉低学分加权后的总 GPA。
        val html = """
            <table id="dataList">
              <tr><th>课程名称</th><th>课程属性</th><th>学分</th><th>成绩</th></tr>
              <tr><td>高等数学A2</td><td>必修</td><td>6.0</td><td>63</td></tr>
              <tr><td>电子工艺实训</td><td>必修</td><td>1.0</td><td>优</td></tr>
            </table>
        """.trimIndent()

        val grades = JwxtHtmlParser.parseGrades(html)

        assertEquals(1.3, grades[0].gpa, 1e-9)
        assertEquals(4.5, grades[1].gpa, 1e-9)
    }

    @Test
    fun `parse grades recomputes gpa from raw score instead of gpa column`() {
        // 绩点由原始成绩换算，页面绩点列与公式不一致时以计算值为准（教务官方口径）。
        val html = """
            <table id="dataList">
              <tr><th>课程名称</th><th>学分</th><th>成绩</th><th>绩点</th></tr>
              <tr><td>线性代数</td><td>3.0</td><td>80</td><td>2.0</td></tr>
            </table>
        """.trimIndent()

        val grades = JwxtHtmlParser.parseGrades(html)

        assertEquals(3.0, grades.single().gpa, 1e-9)
    }

    @Test
    fun `parse grades uses current score for makeup rows and original score as fallback`() {
        // 补考行的绩点按当前有效成绩算（成绩单验证：69 分 1.9）；
        // 仅当“成绩”列缺失时才回退到“原始成绩”列。
        val html = """
            <table id="dataList">
              <tr><th>课程名称</th><th>学分</th><th>成绩</th><th>原始成绩</th><th>绩点</th></tr>
              <tr><td>信息理论与编码B</td><td>2.0</td><td>69</td><td>55</td><td>5.0</td></tr>
              <tr><td>只有原始成绩列</td><td>2.0</td><td></td><td>80</td><td>5.0</td></tr>
            </table>
        """.trimIndent()

        val grades = JwxtHtmlParser.parseGrades(html)

        assertEquals(1.9, grades[0].gpa, 1e-9)
        assertEquals("69", grades[0].scoreText)
        assertEquals(3.0, grades[1].gpa, 1e-9)
    }

    @Test
    fun `parse exams marks jwxt source and calculates days left`() {
        val html = """
            <table id="dataList">
              <tr><th>课程名称</th><th>考试类型</th><th>考试地点</th><th>考试时间</th></tr>
              <tr><td>大学物理</td><td>期末</td><td>教三 402</td><td>2026-06-20 09:00</td></tr>
            </table>
        """.trimIndent()

        val exams = JwxtHtmlParser.parseExams(html, today = java.time.LocalDate.of(2026, 6, 17))

        assertEquals("大学物理", exams.single().name)
        assertEquals(3, exams.single().daysLeft)
        assertEquals(ExamSource.JWXT, exams.single().source)
    }

    @Test
    fun `parse schedule extracts course cell with jwxt source`() {
        val html = """
            <table id="kbtable">
              <tr>
                <td>时间</td><td>星期一</td><td>星期二</td>
              </tr>
              <tr>
                <td>第一大节</td>
                <td>高等数学<br/>张老师<br/>1-3周<br/>一教101</td>
                <td></td>
              </tr>
            </table>
        """.trimIndent()

        val parsed = JwxtHtmlParser.parseSchedule(html)

        assertEquals("高等数学", parsed.courses.single().name)
        assertEquals(1, parsed.courses.single().day)
        assertEquals(1, parsed.courses.single().startSlot)
        assertEquals(CourseSource.JWXT, parsed.courses.single().source)
    }
}
