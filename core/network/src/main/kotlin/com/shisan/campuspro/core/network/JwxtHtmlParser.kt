package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import com.shisan.campuspro.core.model.Exam
import com.shisan.campuspro.core.model.ExamSource
import com.shisan.campuspro.core.model.Grade
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ParsedSchedule(
    val courses: List<Course>,
    val insufficientRows: Boolean = false,
)

data class TermOption(
    val value: String,
    val label: String,
    val selected: Boolean = false,
)

object JwxtHtmlParser {
    fun parseGrades(html: String): List<Grade> = GradeParser.parse(html)

    fun parseExams(html: String, today: LocalDate = LocalDate.now()): List<Exam> = ExamParser.parse(html, today)

    fun parseSchedule(html: String): ParsedSchedule = ScheduleParser.parse(html)

    fun parseTermOptions(html: String): List<TermOption> {
        val doc = Jsoup.parse(html)
        return doc.select("select[name=xnxq01id] option, select#xnxq01id option")
            .mapNotNull { option ->
                val value = option.attr("value").trim()
                val label = option.text().trim()
                if (value.isBlank()) {
                    null
                } else {
                    TermOption(
                        value = value,
                        label = label.ifBlank { value },
                        selected = option.hasAttr("selected"),
                    )
                }
            }
    }

    /**
     * 解析教务系统认定的当前学期：课表页学期下拉框中被选中的选项。
     * 学期切换时机由学校侧控制，因此它比按日期推算更可靠。
     */
    fun parseSelectedTermId(html: String): String? =
        parseTermOptions(html).firstOrNull { it.selected }?.value

    private fun slotFromLabel(label: String, fallbackIndex: Int): Pair<Int, Int> = when {
        label.contains("第一") -> 1 to 2
        label.contains("第二") -> 3 to 4
        label.contains("第三") -> 5 to 6
        label.contains("第四") -> 7 to 8
        label.contains("第五") -> 9 to 10
        else -> (fallbackIndex * 2 + 1) to (fallbackIndex * 2 + 2)
    }

    private fun parseWeeks(text: String): List<Int>? {
        if (!text.contains("周")) return null
        val range = Regex("(\\d+)\\s*-\\s*(\\d+)").find(text)
        if (range != null) {
            val start = range.groupValues[1].toInt()
            val end = range.groupValues[2].toInt()
            return (start..end).toList()
        }
        val single = Regex("(\\d+)").findAll(text).map { it.value.toInt() }.toList()
        return single.ifEmpty { null }
    }

    private fun parseDate(raw: String): LocalDate? {
        val match = Regex("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})").find(raw) ?: return null
        return LocalDate.of(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].toInt(),
        )
    }

    private fun stableId(prefix: String, vararg parts: String): String =
        (listOf(prefix) + parts.map { it.trim() }).joinToString("_")

    private fun String?.toDoubleOrZero(): Double = this?.toDoubleOrNull() ?: 0.0
}
