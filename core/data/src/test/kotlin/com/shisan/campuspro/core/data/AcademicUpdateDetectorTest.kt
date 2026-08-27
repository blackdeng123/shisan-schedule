package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.Exam
import com.shisan.campuspro.core.model.ExamSource
import com.shisan.campuspro.core.model.Grade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicUpdateDetectorTest {
    @Test
    fun `detects new and changed exams but ignores reorder and deletion`() {
        val original = exam(name = "高等数学", date = "2026-06-20 09:00", location = "A101")
        val changed = original.copy(date = "2026-06-21 09:00", location = "A102")
        val added = exam(name = "大学英语", date = "2026-06-22 14:00", location = "B201")

        val diff = AcademicUpdateDetector.compareExams(
            previous = listOf(original),
            current = listOf(changed, added),
        )

        assertEquals(1, diff.addedCount)
        assertEquals(1, diff.updatedCount)
        assertTrue(diff.hasChanges)
        assertFalse(AcademicUpdateDetector.compareExams(listOf(original, added), listOf(added, original)).hasChanges)
        assertFalse(AcademicUpdateDetector.compareExams(listOf(original, added), listOf(original)).hasChanges)
    }

    @Test
    fun `detects new and corrected grades without exposing values in summary`() {
        val original = grade(id = "math", scoreText = "80", gpa = 3.0)
        val corrected = original.copy(score = 85.0, scoreText = "85", gpa = 3.5)
        val added = grade(id = "english", scoreText = "90", gpa = 4.0)

        val diff = AcademicUpdateDetector.compareGrades(
            previous = listOf(original),
            current = listOf(corrected, added),
        )

        assertEquals(1, diff.addedCount)
        assertEquals(1, diff.updatedCount)
        assertEquals("有 1 条新成绩，1 条成绩已更新", diff.summary)
        assertFalse(diff.summary.contains("85"))
    }

    private fun exam(name: String, date: String, location: String) = Exam(
        id = "$name-$date",
        name = name,
        type = "期末",
        credits = 2.0,
        location = location,
        date = date,
        daysLeft = 10,
        source = ExamSource.JWXT,
    )

    private fun grade(id: String, scoreText: String, gpa: Double) = Grade(
        id = id,
        name = id,
        type = "必修",
        credits = 2.0,
        score = scoreText.toDoubleOrNull(),
        scoreText = scoreText,
        gpa = gpa,
        semester = "2025-2026-2",
    )
}
