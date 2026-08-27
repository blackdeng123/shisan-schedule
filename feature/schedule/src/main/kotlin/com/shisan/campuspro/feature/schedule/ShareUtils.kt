package com.shisan.campuspro.feature.schedule

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.shisan.campuspro.core.model.ClassTimeSlot
import com.shisan.campuspro.core.model.Course
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 生成 ICS 日历文件内容
 * 设计参考：https://github.com/YZune/WakeupSchedule_Kotlin/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/utils/ICalUtils.kt
 * 原项目采用 Apache-2.0；当前实现已按本项目数据模型与 java.time API 重写。
 */
fun generateIcsContent(
    courses: List<Course>,
    startDate: LocalDate,
    classTimeSlots: List<ClassTimeSlot>,
    scheduleName: String,
): String {
    val sb = StringBuilder()
    sb.appendLine("BEGIN:VCALENDAR")
    sb.appendLine("VERSION:2.0")
    sb.appendLine("PRODID:-//CampusPro//闪电课表//CN")
    sb.appendLine("CALSCALE:GREGORIAN")
    sb.appendLine("METHOD:PUBLISH")
    sb.appendLine("X-WR-CALNAME:$scheduleName")

    val dayNames = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    val timeFmt = DateTimeFormatter.ofPattern("HHmmss")

    courses.forEach { course ->
        val slotStart = classTimeSlots.getOrNull(course.startSlot - 1)
        val slotEnd = classTimeSlots.getOrNull(course.endSlot - 1)
        if (slotStart == null || slotEnd == null) return@forEach

        // 计算课程的第一次出现日期
        val firstWeekStart = startDate.plusWeeks(
            ((course.weeks.minOrNull() ?: 1) - 1).toLong()
        )
        val firstDate = firstWeekStart.plusDays((course.day - 1).toLong())

        val startParts = slotStart.startTime.split(":").map { it.toIntOrNull() ?: 0 }
        val endParts = slotEnd.endTime.split(":").map { it.toIntOrNull() ?: 0 }

        val startHour = startParts.getOrElse(0) { 8 }
        val startMin = startParts.getOrElse(1) { 0 }
        val endHour = endParts.getOrElse(0) { 8 }
        val endMin = endParts.getOrElse(1) { 45 }

        // 生成 RRULE（重复规则）
        val weeks = course.weeks.sorted()
        val rrule = if (weeks.isEmpty()) {
            "FREQ=WEEKLY;COUNT=20;BYDAY=${dayNames[course.day - 1]}"
        } else {
            // 非连续周次，使用 EXDATE 排除
            val totalWeeks = weeks.max()
            val excludedWeeks = (1..totalWeeks).filter { it !in weeks }
            val exDates = excludedWeeks.map { w ->
                startDate.plusWeeks((w - 1).toLong()).plusDays((course.day - 1).toLong())
            }
            "FREQ=WEEKLY;COUNT=$totalWeeks;BYDAY=${dayNames[course.day - 1]}"
        }

        sb.appendLine("BEGIN:VEVENT")
        sb.appendLine("DTSTART:${dateFmt.format(firstDate)}T${"%02d%02d%02d".format(startHour, startMin, 0)}")
        sb.appendLine("DTEND:${dateFmt.format(firstDate)}T${"%02d%02d%02d".format(endHour, endMin, 0)}")
        sb.appendLine("RRULE:$rrule")
        sb.appendLine("SUMMARY:${course.name}")
        sb.appendLine("LOCATION:${course.location.ifBlank { "待定" }}")
        sb.appendLine("DESCRIPTION:教师: ${course.teacher.ifBlank { "待定" }}\\n教室: ${course.location.ifBlank { "待定" }}")
        sb.appendLine("STATUS:CONFIRMED")
        sb.appendLine("END:VEVENT")
    }

    sb.appendLine("END:VCALENDAR")
    return sb.toString()
}

/**
 * 将 ICS 内容写入临时文件并返回 Uri
 */
fun writeIcsToFile(context: Context, content: String, fileName: String): Uri {
    val dir = File(context.cacheDir, "ics")
    dir.mkdirs()
    val file = File(dir, fileName)
    file.writeText(content)
    val authority = "${context.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, file)
}

/**
 * 分享 ICS 文件
 */
fun shareIcsFile(context: Context, uri: Uri, scheduleName: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/calendar"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "$scheduleName 课表日历")
        putExtra(Intent.EXTRA_TEXT, "来自闪电课表的课表日历文件")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享课表日历"))
}

/**
 * 分享文本
 */
fun shareText(context: Context, text: String, title: String = "分享课表") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, title)
    }
    context.startActivity(Intent.createChooser(intent, title))
}
