package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.AcademicUpdateDiff
import com.shisan.campuspro.core.model.Exam
import com.shisan.campuspro.core.model.Grade

/**
 * 对比前后两批教务数据，计算更新差异摘要。
 * 纯函数，无 I/O，可独立测试。
 */
object AcademicUpdateDetector {
    fun compareExams(previous: List<Exam>, current: List<Exam>): AcademicUpdateDiff {
        val oldGroups = previous.groupBy { it.name.trim() }
        val newGroups = current.groupBy { it.name.trim() }
        var added = 0
        var updated = 0
        newGroups.forEach { (name, exams) ->
            val old = oldGroups[name]
            if (old == null) {
                added += exams.size
            } else {
                val oldFingerprints = old.map(::examFingerprint).toSet()
                val changed = exams.count { examFingerprint(it) !in oldFingerprints }
                if (changed > 0) updated += changed
            }
        }
        return AcademicUpdateDiff(added, updated, "考试安排")
    }

    fun compareGrades(previous: List<Grade>, current: List<Grade>): AcademicUpdateDiff {
        val oldById = previous.associateBy { it.id }
        var added = 0
        var updated = 0
        current.forEach { grade ->
            val old = oldById[grade.id]
            when {
                old == null -> added++
                old.score != grade.score || old.scoreText != grade.scoreText || old.gpa != grade.gpa -> updated++
            }
        }
        return AcademicUpdateDiff(added, updated, "成绩")
    }

    private fun examFingerprint(exam: Exam): String =
        listOf(exam.type.trim(), exam.date.trim(), exam.location.trim()).joinToString("|")
}
