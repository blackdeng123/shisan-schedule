package com.shisan.campuspro.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.designsystem.CampusTheme
import com.shisan.campuspro.core.model.Schedule
import com.shisan.campuspro.core.model.ScheduleDisplaySettings
import com.shisan.campuspro.core.ui.adaptiveContentWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSettingsRoute(
    schedule: Schedule,
    onSave: (name: String, totalWeeks: Int, startDate: String, showWeekend: Boolean, displaySettings: ScheduleDisplaySettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(schedule.name) }
    var totalWeeksStr by remember { mutableStateOf(schedule.totalWeeks.toString()) }
    var startDate by remember { mutableStateOf(schedule.startDate) }
    var showWeekend by remember { mutableStateOf(schedule.displaySettings.showWeekend) }
    var showNonCurrent by remember { mutableStateOf(schedule.displaySettings.showNonCurrentWeekCourses) }
    var courseCardAlpha by remember { mutableFloatStateOf(schedule.displaySettings.courseCardAlpha) }
    var outlineEnabled by remember { mutableStateOf(schedule.displaySettings.outlineEnabled) }
    var showDatePicker by remember { mutableStateOf(false) }
    val parsedStartDate = remember(startDate) {
        runCatching { java.time.LocalDate.parse(startDate) }.getOrNull()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("课表设置") },
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── 基本设置 ──
            SectionLabel("基本设置")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("课表名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = totalWeeksStr,
                onValueChange = { totalWeeksStr = it.filter { c -> c.isDigit() } },
                label = { Text("总周数") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            // ── 开学日期 ──
            val displayDate = parsedStartDate?.let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" } ?: startDate.ifBlank { "未设置" }
            Column {
                Text(
                    text = "开学日期",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = displayDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = { showDatePicker = true }) {
                    Text("修改开学日期")
                }
            }

            // ── 显示设置 ──
            SectionLabel("显示设置")
            SettingSwitchRow(
                title = "显示周末",
                subtitle = "在课表中显示周六和周日",
                checked = showWeekend,
                onCheckedChange = { showWeekend = it },
            )
            SettingSwitchRow(
                title = "显示非本周课程",
                subtitle = "展示非当前周的课程（半透明）",
                checked = showNonCurrent,
                onCheckedChange = { showNonCurrent = it },
            )
            SettingSwitchRow(
                title = "课程卡片描边",
                subtitle = "显示课程卡片的白色边框",
                checked = outlineEnabled,
                onCheckedChange = { outlineEnabled = it },
            )

            // ── 透明度滑块 ──
            Column {
                Text(
                    text = "课程卡片透明度",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${(courseCardAlpha * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = courseCardAlpha,
                    onValueChange = { courseCardAlpha = it },
                    valueRange = 0.3f..1.0f,
                    steps = 7,
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── 保存按钮 ──
            Button(
                onClick = {
                    val weeks = totalWeeksStr.toIntOrNull()?.coerceAtLeast(1) ?: schedule.totalWeeks
                    onSave(
                        name.trim(),
                        weeks,
                        startDate,
                        showWeekend,
                        ScheduleDisplaySettings(
                            showWeekend = showWeekend,
                            showNonCurrentWeekCourses = showNonCurrent,
                            backgroundUri = schedule.displaySettings.backgroundUri,
                            courseCardAlpha = courseCardAlpha,
                            outlineEnabled = outlineEnabled,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "保存设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── DatePicker 对话框 ──
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = parsedStartDate?.toEpochDay()?.let { it * 86400000L },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        startDate = date.toString()
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(name = "课表设置")
@Composable
private fun ScheduleSettingsRoutePreview() {
    CampusTheme {
        ScheduleSettingsRoute(
            schedule = com.shisan.campuspro.core.data.createEmptySchedule("preview").copy(
                name = "2025-2026 第二学期",
                totalWeeks = 20,
            ),
            onSave = { _, _, _, _, _ -> },
            onBack = {},
        )
    }
}
