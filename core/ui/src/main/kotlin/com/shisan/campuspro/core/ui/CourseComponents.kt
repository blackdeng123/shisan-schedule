package com.shisan.campuspro.core.ui

// 界面设计参考：https://github.com/YZune/WakeupSchedule_Kotlin
// WakeUp课程表采用 Apache-2.0；当前 Compose 实现已按本项目数据模型重写。

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shisan.campuspro.core.designsystem.CampusCourseColors
import com.shisan.campuspro.core.designsystem.CampusTheme
import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.ClassTimeSlot
import com.shisan.campuspro.core.model.CourseSource
import com.shisan.campuspro.core.model.DefaultClassTimeSlots
import java.time.DayOfWeek
import java.time.LocalDate

// ────────────────────────────────────────────────
// 常量 —— 等分布局配置
// ────────────────────────────────────────────────

private val WeekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val DayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val TimeColumnWidth = 36.dp
private val SlotHeight = 65.dp
private val HeaderHeight = 50.dp
private val CourseStrokeColor = Color(0x44FFFFFF)

@Composable
fun WeekHeader(
    days: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            Text(day, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CourseCard(
    course: Course,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(course.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(course.location.ifBlank { "待定" }, style = MaterialTheme.typography.labelSmall)
            Text("${course.startSlot}-${course.endSlot}节", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ────────────────────────────────────────────────
// 周课表网格 —— 等分布局（无横向滚动）
// ────────────────────────────────────────────────

@Composable
fun WeeklyCourseGrid(
    courses: List<Course>,
    selectedWeek: Int,
    modifier: Modifier = Modifier,
    weekDates: List<LocalDate> = emptyList(),
    totalSlots: Int = 10,
    classTimeSlots: List<ClassTimeSlot> = DefaultClassTimeSlots,
    showNonCurrentWeekCourses: Boolean = true,
    onCoursesClick: (List<Course>) -> Unit = {},
    onEmptyAreaClick: () -> Unit = {},
) {
    val today = LocalDate.now()
    val todayDayOfWeek = today.dayOfWeek.value // 1=Mon, 7=Sun

    val allCourses by remember(courses, selectedWeek) {
        derivedStateOf {
            courses.filter { it.day in 1..7 }
        }
    }
    val currentWeekCourses by remember(allCourses, selectedWeek) {
        derivedStateOf { allCourses.filter { it.weeks.isEmpty() || selectedWeek in it.weeks } }
    }
    val otherWeekCourses by remember(allCourses, selectedWeek, showNonCurrentWeekCourses) {
        derivedStateOf {
            if (!showNonCurrentWeekCourses) emptyList()
            else allCourses.filter { it.weeks.isNotEmpty() && selectedWeek !in it.weeks }
        }
    }
    val slotCount = remember(courses, totalSlots) {
        maxOf(totalSlots, courses.maxOfOrNull { it.endSlot } ?: totalSlots).coerceAtLeast(1)
    }
    val groupedCurrent by remember(currentWeekCourses, slotCount) {
        derivedStateOf {
            currentWeekCourses
                .filter { it.startSlot in 1..slotCount }
                .groupBy { CoursePosition(day = it.day, startSlot = it.startSlot) }
        }
    }
    val groupedOther by remember(otherWeekCourses, slotCount) {
        derivedStateOf {
            otherWeekCourses
                .filter { it.startSlot in 1..slotCount }
                .groupBy { CoursePosition(day = it.day, startSlot = it.startSlot) }
        }
    }

    val gridHeight = SlotHeight * slotCount
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Column(modifier = modifier.fillMaxWidth()) {
        // ── 表头：时间列 + 7 天等分 ──
        Row(modifier = Modifier.fillMaxWidth().requiredHeight(HeaderHeight)) {
            // 左上角月份
            MonthHeader(
                date = weekDates.firstOrNull(),
                modifier = Modifier
                    .width(TimeColumnWidth)
                    .fillMaxHeight(),
            )
            // 7 天表头，各占 1/7
            WeekdayLabels.forEachIndexed { index, label ->
                val dayNum = index + 1
                val isToday = dayNum == todayDayOfWeek &&
                        weekDates.getOrNull(index) == today
                DayHeaderCell(
                    weekday = label,
                    date = weekDates.getOrNull(index),
                    isToday = isToday,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        // ── 课表主体：时间列 + 7 天课程列 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(gridHeight),
        ) {
            // 左侧节次时间
            SlotLabels(
                slotCount = slotCount,
                classTimeSlots = classTimeSlots,
                modifier = Modifier.width(TimeColumnWidth),
            )
            // 7 天课程区域，等分
            (1..7).forEach { day ->
                val emptyAreaInteractionSource = remember(day) { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("schedule-day-$day")
                        .clickable(
                            interactionSource = emptyAreaInteractionSource,
                            indication = null,
                            onClick = onEmptyAreaClick,
                        )
                        .border(0.5.dp, gridLineColor),
                ) {
                    // 非本周课程（底层）
                    groupedOther
                        .filter { it.key.day == day }
                        .forEach { (position, coursesInSlot) ->
                            key("other-${position.day}-${position.startSlot}") {
                                TimetableCourseStack(
                                    courses = coursesInSlot,
                                    selectedWeek = selectedWeek,
                                    slotCount = slotCount,
                                    onCoursesClick = onCoursesClick,
                                    isOtherWeek = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(y = SlotHeight * (position.startSlot - 1))
                                        .requiredHeight(courseHeight(coursesInSlot, slotCount)),
                                )
                            }
                        }
                    // 当前周课程（顶层）
                    groupedCurrent
                        .filter { it.key.day == day }
                        .forEach { (position, coursesInSlot) ->
                            key("${position.day}-${position.startSlot}") {
                                TimetableCourseStack(
                                    courses = coursesInSlot,
                                    selectedWeek = selectedWeek,
                                    slotCount = slotCount,
                                    onCoursesClick = onCoursesClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(y = SlotHeight * (position.startSlot - 1))
                                        .requiredHeight(courseHeight(coursesInSlot, slotCount)),
                                )
                            }
                        }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────
// 表头组件
// ────────────────────────────────────────────────

@Composable
private fun MonthHeader(
    date: LocalDate?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = (date?.monthValue ?: LocalDate.now().monthValue).toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "月",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun DayHeaderCell(
    weekday: String,
    date: LocalDate?,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val textColor = if (isToday) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    }
    val fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
    Column(
        modifier = modifier.padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = weekday,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = fontWeight,
            color = textColor,
            maxLines = 1,
            fontSize = 11.sp,
        )
        Text(
            text = date?.let { "${it.monthValue}/${it.dayOfMonth}" }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = fontWeight,
            color = textColor,
            maxLines = 1,
            fontSize = 10.sp,
        )
    }
}

// ────────────────────────────────────────────────
// 节次时间列
// ────────────────────────────────────────────────

@Composable
private fun SlotLabels(
    slotCount: Int,
    classTimeSlots: List<ClassTimeSlot>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.requiredHeight(SlotHeight * slotCount)) {
        (1..slotCount).forEach { slot ->
            Column(
                modifier = Modifier
                    .requiredWidth(TimeColumnWidth)
                    .requiredHeight(SlotHeight)
                    .padding(top = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = slot.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                )
                Text(
                    text = classTimeSlots
                        .firstOrNull { it.index == slot }
                        ?.let { "${it.startTime}\n${it.endTime}" }
                        .orEmpty(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    lineHeight = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ────────────────────────────────────────────────
// 课程卡片堆叠
// ────────────────────────────────────────────────

@Composable
private fun TimetableCourseStack(
    courses: List<Course>,
    selectedWeek: Int,
    slotCount: Int,
    onCoursesClick: (List<Course>) -> Unit,
    isOtherWeek: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val visibleCourses = courses.take(2)
    val hiddenCount = courses.size - visibleCourses.size
    Column(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        visibleCourses.forEach { course ->
            TimetableCourseCard(
                course = course,
                selectedWeek = selectedWeek,
                slotSpan = (course.endSlot.coerceIn(course.startSlot, slotCount) - course.startSlot + 1)
                    .coerceAtLeast(1),
                onClick = { onCoursesClick(courses) },
                isOtherWeek = isOtherWeek,
                modifier = Modifier.weight(1f),
            )
        }
        if (hiddenCount > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredSizeIn(minHeight = 20.dp)
                    .clickable { onCoursesClick(courses) }
                    .semantics { contentDescription = "还有${hiddenCount}门同时间课程" },
                shape = RoundedCornerShape(4.dp),
                color = Color(0xCC94A3B8).copy(alpha = if (isOtherWeek) 0.3f else 1f),
                contentColor = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "+$hiddenCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────
// 单个课程卡片 —— 仿 WakeupSchedule TipTextView
// ────────────────────────────────────────────────

@Composable
private fun TimetableCourseCard(
    course: Course,
    selectedWeek: Int,
    slotSpan: Int,
    onClick: () -> Unit,
    isOtherWeek: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val containerColor = remember(course.color) { parseCourseColor(course.color) }
    val interactionSource = remember(course.id) { MutableInteractionSource() }
    val alpha = if (isOtherWeek) 0.3f else 0.82f
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .testTag("course-${course.id}")
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .border(1.dp, CourseStrokeColor.copy(alpha = if (isOtherWeek) 0.15f else 0.3f), RoundedCornerShape(4.dp))
            .semantics {
                contentDescription = buildCourseDescription(course, selectedWeek)
            },
        shape = RoundedCornerShape(4.dp),
        color = containerColor.copy(alpha = alpha),
        contentColor = Color.White.copy(alpha = if (isOtherWeek) 0.3f else 1f),
    ) {
        Box {
            Text(
                text = buildCourseDisplayText(course),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
                fontWeight = FontWeight.Bold,
                maxLines = if (slotSpan <= 1) 3 else 6,
                overflow = TextOverflow.Clip,
            )
            // 冲突角标
            if (course.conflict) {
                Text(
                    text = "⚠",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(1.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color(0xFFE53935),
                )
            }
        }
    }
}

// ────────────────────────────────────────────────
// 工具函数
// ────────────────────────────────────────────────

private fun courseHeight(
    courses: List<Course>,
    slotCount: Int,
): Dp {
    val course = courses.firstOrNull() ?: return SlotHeight
    val endSlot = course.endSlot.coerceIn(course.startSlot, slotCount)
    val span = (endSlot - course.startSlot + 1).coerceAtLeast(1)
    return SlotHeight * span
}

private fun parseCourseColor(value: String): Color =
    CampusCourseColors.resolve(value)

// 仿 WakeupSchedule：课程名\n@教室（不显示教师）
private fun buildCourseDisplayText(course: Course): String {
    val location = course.location.ifBlank { "待定" }
    return "${course.name}\n@$location"
}

private fun buildCourseDescription(course: Course, selectedWeek: Int): String {
    val day = DayNames.getOrNull(course.day - 1) ?: "未知星期"
    val location = course.location.ifBlank { "地点待定" }
    val teacher = course.teacher.ifBlank { "教师待定" }
    return "${course.name}，$day，第${course.startSlot}到${course.endSlot}节，$location，$teacher，第${selectedWeek}周"
}

@Stable
private data class CoursePosition(
    val day: Int,
    val startSlot: Int,
)

@Composable
fun SemesterSelector(
    schedules: List<String>,
    selected: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        schedules.take(3).forEach { label ->
            FilterChip(
                selected = label == selected,
                onClick = { onSelected(label) },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
        if (schedules.size > 3) {
            Spacer(modifier = Modifier.size(1.dp))
        }
    }
}

// ────────────────────────────────────────────────
// Preview
// ────────────────────────────────────────────────

@Preview(name = "WakeUp Style Weekly Grid")
@Composable
private fun WeeklyCourseGridPreview() {
    CampusTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE7ECF8)),
        ) {
            WeeklyCourseGrid(
                courses = previewCourses,
                selectedWeek = 3,
                weekDates = (0..6).map { LocalDate.of(2026, 4, 27).plusDays(it.toLong()) },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                totalSlots = 10,
            )
        }
    }
}

@Preview(name = "WakeUp Style Weekly Grid Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WeeklyCourseGridDarkPreview() {
    CampusTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111827)),
        ) {
            WeeklyCourseGrid(
                courses = previewCourses,
                selectedWeek = 3,
                weekDates = (0..6).map { LocalDate.of(2026, 4, 27).plusDays(it.toLong()) },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                totalSlots = 10,
            )
        }
    }
}

private val previewCourses = listOf(
    previewCourse(id = "1", name = "专业综合实践\n课程设计", day = 1, startSlot = 1, endSlot = 2, color = "#E96C93"),
    previewCourse(id = "2", name = "大学英语", day = 1, startSlot = 3, endSlot = 4, color = "#72A5F2"),
    previewCourse(id = "3", name = "大学英语", day = 1, startSlot = 5, endSlot = 6, color = "#72A5F2"),
    previewCourse(id = "4", name = "线性代数", day = 2, startSlot = 3, endSlot = 4, color = "#E49AB1"),
    previewCourse(id = "5", name = "线性代数", day = 3, startSlot = 1, endSlot = 2, color = "#E49AB1"),
    previewCourse(id = "6", name = "大学物理", day = 3, startSlot = 3, endSlot = 4, color = "#69D2C1"),
    previewCourse(id = "7", name = "专业综合实践\n课程设计", day = 4, startSlot = 3, endSlot = 4, color = "#E65C86"),
    previewCourse(id = "8", name = "大学物理", day = 5, startSlot = 3, endSlot = 4, color = "#69D2C1"),
)

private fun previewCourse(
    id: String,
    name: String,
    day: Int,
    startSlot: Int,
    endSlot: Int,
    color: String,
    weeks: List<Int> = emptyList(),
) = Course(
    id = id,
    name = name,
    location = "教室甲",
    teacher = "教师甲，教师乙",
    day = day,
    startSlot = startSlot,
    endSlot = endSlot,
    color = color,
    weeks = weeks,
    source = CourseSource.JWXT,
    jwxtKey = id,
)
