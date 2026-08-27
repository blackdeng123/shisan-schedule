package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import com.shisan.campuspro.core.model.Exam
import com.shisan.campuspro.core.model.ExamSource
import com.shisan.campuspro.core.model.GpaCalculator
import com.shisan.campuspro.core.model.Grade
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.jsoup.Jsoup

object ScheduleParser {
    private val separator = Regex("-{10,}")
    private val fontTitle = Regex("""<font\s+title=["']([^"']+)["'][^>]*>([^<]*)</font>""")

    fun parse(html: String): ParsedSchedule {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("kbtable") ?: return ParsedSchedule(emptyList())
        val rows = table.select("tr")
        val dayColumns = rows
            .asSequence()
            .map { it.directCells() }
            .mapNotNull { cells ->
                cells.mapIndexedNotNull { index, cell ->
                    dayFromHeader(cell.text())?.let { day -> index to day }
                }.toMap().takeIf { it.isNotEmpty() }
            }
            .firstOrNull()
            ?: return ParsedSchedule(emptyList(), insufficientRows = true)

        val teacherMap = doc.select("div.kbcontent").associateTeacherByCell()
        val courses = rows.flatMap { row ->
            val cells = row.directCells()
            val slot = cells.firstNotNullOfOrNull { slotFromLabel(it.text()) }
                ?: return@flatMap emptyList()
            dayColumns.flatMap { (columnIndex, day) ->
                val cell = cells.getOrNull(columnIndex) ?: return@flatMap emptyList()
                val visibleDivs = cell.select("div.kbcontent1")
                if (visibleDivs.isNotEmpty()) {
                    visibleDivs.flatMap { div ->
                        val baseId = div.id().removeSuffix("-1")
                        splitSegments(div.html()).mapIndexedNotNull { segmentIndex, segment ->
                            val extracted = extractCourse(segment)
                            extracted.toCourse(
                                teacher = teacherMap[baseId to segmentIndex] ?: extracted.teacher,
                                day = day,
                                slot = slot,
                            )
                        }
                    }
                } else {
                    parsePlainCell(cell.wholeText(), day, slot)
                }
            }
        }
        return ParsedSchedule(courses)
    }

    private fun org.jsoup.select.Elements.associateTeacherByCell(): Map<Pair<String, Int>, String> =
        buildMap {
            val iterator = this@associateTeacherByCell.iterator()
            while (iterator.hasNext()) {
                val div: org.jsoup.nodes.Element = iterator.next()
                val baseId = div.attr("id").removeSuffix("-2")
                splitSegments(div.html()).forEachIndexed { index, segment ->
                    val teacher = extractCourse(segment).teacher
                    if (teacher.isNotBlank()) put(baseId to index, teacher)
                }
            }
        }

    private fun splitSegments(html: String): List<String> =
        separator.split(html)
            .map { it.trim().trimStartBreaks() }
            .filter { it.isNotBlank() && it != "&nbsp;" }

    private fun extractCourse(html: String): ExtractedCourse {
        val text = html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "&nbsp;" && !separator.matches(it) }
        val byTitle = fontTitle.findAll(html).associate { it.groupValues[1] to it.groupValues[2].trim() }
        return ExtractedCourse(
            name = text.firstOrNull().orEmpty(),
            teacher = byTitle["老师"].orEmpty(),
            weeks = parseWeeks(byTitle["周次(节次)"].orEmpty()),
            location = byTitle["教室"].orEmpty(),
        )
    }

    private fun parsePlainCell(text: String, day: Int, slot: Pair<Int, Int>): List<Course> {
        val parts = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isEmpty()) return emptyList()
        val weeks = parts.firstNotNullOfOrNull { parseWeeks(it).takeIf { parsed -> parsed.isNotEmpty() } }.orEmpty()
        return listOfNotNull(
            ExtractedCourse(
                name = parts.first(),
                teacher = parts.getOrNull(1).orEmpty(),
                weeks = weeks,
                location = parts.lastOrNull().orEmpty(),
            ).toCourse(day = day, slot = slot),
        )
    }

    private fun ExtractedCourse.toCourse(
        day: Int,
        slot: Pair<Int, Int>,
        teacher: String = this.teacher,
    ): Course? {
        if (name.isBlank()) return null
        val key = stableId("jwxt", name, day.toString(), slot.first.toString(), location, weeks.joinToString("-"))
        return Course(
            id = key,
            name = name,
            location = location,
            teacher = teacher,
            day = day,
            startSlot = slot.first,
            endSlot = slot.second,
            color = "",
            weeks = weeks,
            source = CourseSource.JWXT,
            jwxtKey = key,
        )
    }

    private fun org.jsoup.nodes.Element.directCells(): List<org.jsoup.nodes.Element> =
        children().filter { it.tagName() == "th" || it.tagName() == "td" }

    private fun dayFromHeader(label: String): Int? = when {
        label.contains("星期一") -> 1
        label.contains("星期二") -> 2
        label.contains("星期三") -> 3
        label.contains("星期四") -> 4
        label.contains("星期五") -> 5
        label.contains("星期六") -> 6
        label.contains("星期日") || label.contains("星期天") -> 7
        else -> null
    }

    private fun slotFromLabel(label: String): Pair<Int, Int>? = when {
        label.contains("第一") -> 1 to 2
        label.contains("第二") -> 3 to 4
        label.contains("第三") -> 5 to 6
        label.contains("第四") -> 7 to 8
        label.contains("第五") -> 9 to 10
        else -> null
    }

    private fun parseWeeks(text: String): List<Int> {
        if (!text.contains("周")) return emptyList()
        val oddOnly = text.contains("单周")
        val evenOnly = text.contains("双周")
        val weeks = mutableSetOf<Int>()
        Regex("(\\d+)\\s*-\\s*(\\d+)").findAll(text).forEach { match ->
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toInt()
            (start..end).forEach { weeks += it }
        }
        Regex("(?<![-\\d])(\\d+)(?!\\s*-)").findAll(text).forEach { match ->
            weeks += match.groupValues[1].toInt()
        }
        return weeks.sorted().filter {
            (!oddOnly || it % 2 == 1) && (!evenOnly || it % 2 == 0)
        }
    }

    private fun String.trimStartBreaks(): String =
        replace(Regex("""(?i)^(<br\s*/?>\s*)+"""), "")

    private data class ExtractedCourse(
        val name: String,
        val teacher: String,
        val weeks: List<Int>,
        val location: String,
    )
}

object GradeParser {
    fun parse(html: String): List<Grade> {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("dataList") ?: return emptyList()
        val rows = table.select("tr")
        if (rows.size < 2) return emptyList()
        val headers = rows.first()!!.select("th,td").map { it.text().trim() }

        return rows.drop(1).mapNotNull { row ->
            val values = row.select("td").map { it.text().trim() }
            if (values.isEmpty()) return@mapNotNull null
            val record = headers.zip(values).toMap()
            val name = record["课程名称"] ?: record["课程"] ?: return@mapNotNull null
            val scoreText = record["成绩"] ?: record["总成绩"] ?: ""
            // 绩点一律由原始成绩数值换算（教务官方口径）；“成绩/总成绩”列为当前有效成绩，
            // 补考/重修行也按它计算（成绩单已验证），仅当成绩列缺失才回退到“原始成绩”列。
            val rawScoreText = scoreText.takeIf { it.isNotBlank() } ?: record["原始成绩"] ?: ""
            val semesterText = record["学期"] ?: record["开课学期"] ?: record["学年学期"] ?: ""
            Grade(
                id = stableId("grade", name, semesterText),
                name = name,
                type = record["课程属性"] ?: record["修读性质"] ?: "",
                credits = record["学分"].toDoubleOrZero(),
                score = scoreText.toDoubleOrNull(),
                scoreText = scoreText,
                gpa = rawScoreText.toDoubleOrNull()?.let(GpaCalculator::scoreToGpa)
                    ?: GpaCalculator.levelToGpa(rawScoreText)
                    ?: (record["绩点"] ?: record["学分绩点"])?.toDoubleOrNull()
                    ?: 0.0,
                semester = semesterText.takeIf { it.isNotBlank() },
            )
        }
    }
}

object ExamParser {
    fun parse(html: String, today: LocalDate = LocalDate.now()): List<Exam> {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("dataList") ?: return emptyList()
        val rows = table.select("tr")
        if (rows.size < 2) return emptyList()
        val headers = rows.first()!!.select("th,td").map { it.text().trim() }

        return rows.drop(1).mapNotNull { row ->
            val values = row.select("td").map { it.text().trim() }
            if (values.isEmpty()) return@mapNotNull null
            val record = headers.zip(values).toMap()
            val name = record["课程名称"] ?: record["考试科目"] ?: return@mapNotNull null
            val rawDate = record["考试时间"] ?: record["考试日期"] ?: ""
            val date = parseDate(rawDate)
            Exam(
                id = stableId("exam", name, rawDate),
                name = name,
                type = record["考试类型"] ?: "期末",
                credits = record["学分"].toDoubleOrZero(),
                location = record["考试地点"] ?: record["考场"] ?: "待定",
                date = rawDate,
                daysLeft = date?.let { ChronoUnit.DAYS.between(today, it).toInt() } ?: 999,
                source = ExamSource.JWXT,
            )
        }.sortedBy { it.daysLeft }
    }
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
