package com.shisan.campuspro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 绩点规则以学校官方学业成绩单为基准验证：
 * 百分制绩点 = (成绩-60)/10 + 1，总 GPA 为学分加权平均。
 */
class GpaCalculatorTest {
    @Test
    fun `百分制绩点与教务成绩单一致`() {
        // 教务换算口径样例：96 分 4.6、60 分 1.0、82 分 3.2、63 分 1.3
        assertEquals(4.6, GpaCalculator.scoreToGpa(96.0), 1e-9)
        assertEquals(1.0, GpaCalculator.scoreToGpa(60.0), 1e-9)
        assertEquals(3.2, GpaCalculator.scoreToGpa(82.0), 1e-9)
        assertEquals(1.3, GpaCalculator.scoreToGpa(63.0), 1e-9)
        // 不及格绩点为 0
        assertEquals(0.0, GpaCalculator.scoreToGpa(59.0), 1e-9)
    }

    @Test
    fun `等级制绩点换算覆盖优良及格与不及格`() {
        // 等级制课程仅出现"优=4.5"与"良=3.5"两档
        assertEquals(4.5, GpaCalculator.levelToGpa("优"))
        assertEquals(3.5, GpaCalculator.levelToGpa("良"))
        assertEquals(0.0, GpaCalculator.levelToGpa("不及格"))
        assertEquals(0.0, GpaCalculator.levelToGpa("不合格"))
        assertEquals(1.5, GpaCalculator.levelToGpa("及格"))
        assertNull(GpaCalculator.levelToGpa("90"))
    }

    @Test
    fun `总绩点按学分加权而非简单平均`() {
        // 高学分低绩点的课程必须拉低总绩点：
        // 简单平均 2.58，加权平均 2.03，后者才符合教务口径。
        val grades = listOf(
            grade("课程甲", credits = 6.0, gpa = 1.3),
            grade("课程乙", credits = 3.5, gpa = 1.3),
            grade("课程丙", credits = 3.0, gpa = 1.1),
            grade("课程丁", credits = 1.0, gpa = 4.6),
            grade("课程戊", credits = 1.0, gpa = 4.0),
            grade("课程己", credits = 4.5, gpa = 3.2),
        )

        val weighted = GpaCalculator.creditWeightedGpa(grades)!!
        assertEquals(38.65 / 19.0, weighted, 1e-9)
        assertEquals(2.03, weighted, 0.005)
    }

    @Test
    fun `零学分课程不参与加权`() {
        val grades = listOf(
            grade("正课", credits = 2.0, gpa = 3.0),
            grade("零学分记录", credits = 0.0, gpa = 0.0),
        )
        assertEquals(3.0, GpaCalculator.creditWeightedGpa(grades)!!, 1e-9)
    }

    @Test
    fun `无有效课程时返回空`() {
        assertNull(GpaCalculator.creditWeightedGpa(emptyList()))
        assertNull(GpaCalculator.creditWeightedGpa(listOf(grade("零学分", credits = 0.0, gpa = 2.0))))
    }

    private fun grade(name: String, credits: Double, gpa: Double) = Grade(
        id = name,
        name = name,
        type = "必修",
        credits = credits,
        score = null,
        scoreText = "",
        gpa = gpa,
        semester = "2023-2024-1",
    )
}
