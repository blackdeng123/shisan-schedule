package com.shisan.campuspro.feature.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shisan.campuspro.core.designsystem.CampusColors
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.designsystem.CampusTheme
import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseEditDraft
import com.shisan.campuspro.core.model.CourseSource
import com.shisan.campuspro.core.ui.adaptiveContentWidth
import java.util.UUID

// ────────────────────────────────────────────────
// 预设颜色方案（简化版，不照搬 25KB HSV ColorPickerView）
// ────────────────────────────────────────────────

private val PresetColors = listOf(
    "#E96C93", "#E65C86", "#E49AB1", // 粉色系
    "#72A5F2", "#5B8DEF", "#1E88E5", // 蓝色系
    "#69D2C1", "#4CAF50", "#81C784", // 绿色系
    "#FFB74D", "#FF9800", "#F57C00", // 橙色系
    "#BA68C8", "#9C27B0", "#7B1FA2", // 紫色系
    "#90A4AE", "#78909C", "#546E7A", // 灰蓝色系
)

private val DayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

// ────────────────────────────────────────────────
// Route 入口
// ────────────────────────────────────────────────

@Composable
fun CourseEditRoute(
    scheduleId: String,
    totalWeeks: Int,
    totalSlots: Int,
    existingCourse: Course? = null,
    onSave: (Course) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEditing = existingCourse != null
    val draft = existingCourse?.toEditDraft() ?: CourseEditDraft()

    var name by remember(existingCourse) { mutableStateOf(draft.name) }
    var teacher by remember(existingCourse) { mutableStateOf(draft.teacher) }
    var location by remember(existingCourse) { mutableStateOf(draft.location) }
    var selectedDay by remember(existingCourse) { mutableIntStateOf(draft.day) }
    var startSlot by remember(existingCourse) { mutableIntStateOf(draft.startSlot) }
    var endSlot by remember(existingCourse) { mutableIntStateOf(draft.endSlot) }
    var selectedColor by remember(existingCourse) { mutableStateOf(draft.color) }
    val selectedWeeks = remember(existingCourse) {
        mutableStateListOf<Int>().apply {
            if (draft.weeks.isNotEmpty()) addAll(draft.weeks)
        }
    }

    var nameError by remember(existingCourse) { mutableStateOf(false) }
    var slotError by remember(existingCourse) { mutableStateOf(false) }

    CourseEditScreen(
        isEditing = isEditing,
        name = name,
        onNameChange = { name = it; nameError = false },
        nameError = nameError,
        teacher = teacher,
        onTeacherChange = { teacher = it },
        location = location,
        onLocationChange = { location = it },
        selectedDay = selectedDay,
        onDaySelected = { selectedDay = it },
        startSlot = startSlot,
        endSlot = endSlot,
        onStartSlotChange = { startSlot = it },
        onEndSlotChange = { endSlot = it },
        slotError = slotError,
        totalSlots = totalSlots,
        selectedColor = selectedColor,
        onColorSelected = { selectedColor = it },
        totalWeeks = totalWeeks,
        selectedWeeks = selectedWeeks.toList(),
        onWeekToggle = { week ->
            if (week in selectedWeeks) selectedWeeks.remove(week)
            else selectedWeeks.add(week)
        },
        onSelectAllWeeks = {
            selectedWeeks.clear()
            selectedWeeks.addAll(1..totalWeeks)
        },
        onClearWeeks = { selectedWeeks.clear() },
        onSave = {
            // 验证
            if (name.isBlank()) { nameError = true; return@CourseEditScreen }
            if (startSlot > endSlot || startSlot < 1 || endSlot > totalSlots) {
                slotError = true; return@CourseEditScreen
            }

            val course = Course(
                id = existingCourse?.id ?: UUID.randomUUID().toString(),
                name = name.trim(),
                location = location.trim(),
                teacher = teacher.trim(),
                day = selectedDay,
                startSlot = startSlot,
                endSlot = endSlot,
                color = selectedColor,
                weeks = selectedWeeks.sorted(),
                source = existingCourse?.source ?: CourseSource.MANUAL,
                jwxtKey = existingCourse?.jwxtKey ?: "",
            )
            onSave(course)
        },
        onBack = onBack,
        modifier = modifier,
    )
}

// ────────────────────────────────────────────────
// Screen Composable
// ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CourseEditScreen(
    isEditing: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    nameError: Boolean,
    teacher: String,
    onTeacherChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    startSlot: Int,
    endSlot: Int,
    onStartSlotChange: (Int) -> Unit,
    onEndSlotChange: (Int) -> Unit,
    slotError: Boolean,
    totalSlots: Int,
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    totalWeeks: Int,
    selectedWeeks: List<Int>,
    onWeekToggle: (Int) -> Unit,
    onSelectAllWeeks: () -> Unit,
    onClearWeeks: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "编辑课程" else "添加课程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CampusIcons.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .adaptiveContentWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── 基本信息 ──
            SectionLabel("基本信息")
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("课程名称") },
                placeholder = { Text("例如：高等数学") },
                isError = nameError,
                supportingText = if (nameError) {{ Text("请输入课程名称") }} else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = teacher,
                onValueChange = onTeacherChange,
                label = { Text("教师") },
                placeholder = { Text("选填") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = location,
                onValueChange = onLocationChange,
                label = { Text("教室") },
                placeholder = { Text("选填") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            // ── 星期 ──
            SectionLabel("上课星期")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayLabels.forEachIndexed { index, label ->
                    val day = index + 1
                    FilterChip(
                        selected = day == selectedDay,
                        onClick = { onDaySelected(day) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CampusColors.ChipSelectedContainer,
                            selectedLabelColor = CampusColors.ChipSelectedOnContainer,
                        ),
                    )
                }
            }

            // ── 节次 ──
            SectionLabel("上课节次")
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SlotPicker(
                    label = "开始",
                    value = startSlot,
                    range = 1..totalSlots,
                    onValueChange = onStartSlotChange,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                SlotPicker(
                    label = "结束",
                    value = endSlot,
                    range = startSlot..totalSlots,
                    onValueChange = onEndSlotChange,
                    modifier = Modifier.weight(1f),
                )
            }
            if (slotError) {
                Text(
                    text = "节次范围不合法",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()

            // ── 周次 ──
            SectionLabel(
                title = "上课周次",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onSelectAllWeeks, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("全选", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = onClearWeeks, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("清除", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
            )
            Text(
                text = if (selectedWeeks.isEmpty()) "每周" else selectedWeeks.sorted().joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (1..totalWeeks).forEach { week ->
                    WeekChip(
                        week = week,
                        selected = week in selectedWeeks,
                        onClick = { onWeekToggle(week) },
                    )
                }
            }

            HorizontalDivider()

            // ── 颜色 ──
            SectionLabel("课程颜色")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PresetColors.forEach { color ->
                    ColorDot(
                        color = color,
                        selected = color == selectedColor,
                        onClick = { onColorSelected(color) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 保存按钮 ──
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = if (isEditing) "保存修改" else "添加课程",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ────────────────────────────────────────────────
// 辅助组件
// ────────────────────────────────────────────────

@Composable
private fun SectionLabel(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

@Composable
private fun SlotPicker(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 减少按钮
        SmallCircleButton(
            text = "−",
            enabled = value > range.first,
            onClick = { onValueChange((value - 1).coerceAtLeast(range.first)) },
        )
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center,
        )
        // 增加按钮
        SmallCircleButton(
            text = "+",
            enabled = value < range.last,
            onClick = { onValueChange((value + 1).coerceAtMost(range.last)) },
        )
    }
}

@Composable
private fun SmallCircleButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = text },
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WeekChip(
    week: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val textColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderModifier = if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
    else Modifier

    Surface(
        modifier = Modifier
            .size(width = 42.dp, height = 36.dp)
            .then(borderModifier)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "第${week}周" },
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "$week",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
            )
        }
    }
}

@Composable
private fun ColorDot(
    color: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val parsedColor = remember(color) {
        runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrDefault(Color.Gray)
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(parsedColor)
            .then(
                if (selected) Modifier.border(3.dp, Color.White, CircleShape)
                    .border(1.5.dp, parsedColor, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "颜色 $color" },
    )
}

// ────────────────────────────────────────────────
// 转换工具
// ────────────────────────────────────────────────

private fun Course.toEditDraft() = CourseEditDraft(
    id = id,
    name = name,
    location = location,
    teacher = teacher,
    day = day,
    startSlot = startSlot,
    endSlot = endSlot,
    color = color,
    weeks = weeks,
)

// ────────────────────────────────────────────────
// Preview
// ────────────────────────────────────────────────

@Preview(name = "添加课程")
@Composable
private fun CourseEditRoutePreview() {
    CampusTheme {
        CourseEditRoute(
            scheduleId = "preview",
            totalWeeks = 20,
            totalSlots = 10,
            existingCourse = null,
            onSave = {},
            onBack = {},
        )
    }
}

@Preview(name = "编辑课程")
@Composable
private fun CourseEditRouteEditPreview() {
    CampusTheme {
        CourseEditRoute(
            scheduleId = "preview",
            totalWeeks = 20,
            totalSlots = 10,
            existingCourse = Course(
                id = "1",
                name = "高等数学A",
                location = "教室甲",
                teacher = "张三",
                day = 1,
                startSlot = 1,
                endSlot = 2,
                color = "#72A5F2",
                weeks = listOf(1, 3, 5, 7, 9, 11, 13, 15),
                source = CourseSource.MANUAL,
                jwxtKey = "",
            ),
            onSave = {},
            onBack = {},
        )
    }
}
