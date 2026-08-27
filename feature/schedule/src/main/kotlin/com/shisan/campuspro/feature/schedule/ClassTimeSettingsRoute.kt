package com.shisan.campuspro.feature.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TabRow
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.designsystem.CampusTheme
import com.shisan.campuspro.core.model.ClassTimeSlot
import com.shisan.campuspro.core.model.ClassTimeSeason
import com.shisan.campuspro.core.model.DefaultClassTimeSlots
import com.shisan.campuspro.core.model.appendClassTimeSlot
import com.shisan.campuspro.core.model.canRemoveLastClassTimeSlot
import com.shisan.campuspro.core.ui.adaptiveContentWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassTimeSettingsRoute(
    slots: List<ClassTimeSlot>,
    season: ClassTimeSeason,
    maxUsedSlot: Int,
    onSave: (List<ClassTimeSlot>) -> Unit,
    onSeasonSelected: (ClassTimeSeason) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editedSlots by remember(slots) { mutableStateOf(slots.sortedBy { it.index }) }
    var editingSlot by remember { mutableStateOf<ClassTimeSlot?>(null) }
    var selectedSeason by remember { mutableStateOf(season) }

    LaunchedEffect(season) {
        selectedSeason = season
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("上课时间") },
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
                .padding(padding),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                ClassTimeSeason.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = selectedSeason == item,
                        onClick = {
                            selectedSeason = item
                            onSeasonSelected(item)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ClassTimeSeason.entries.size,
                        ),
                        label = { Text(if (item == ClassTimeSeason.SUMMER) "夏令时" else "冬令时") },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                itemsIndexed(editedSlots) { _, slot ->
                    TimeSlotCard(
                        slot = slot,
                        onClick = { editingSlot = slot },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            onClick = { editedSlots = editedSlots.dropLast(1) },
                            enabled = canRemoveLastClassTimeSlot(editedSlots, maxUsedSlot),
                            modifier = Modifier.weight(1f),
                        ) { Text("减少一节") }
                        TextButton(
                            onClick = { editedSlots = appendClassTimeSlot(editedSlots) },
                            modifier = Modifier.weight(1f),
                        ) { Text("添加一节") }
                    }
                }
            }

            // 底部保存按钮
            Button(
                onClick = { onSave(editedSlots) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "保存时间设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    // 时间选择对话框
    editingSlot?.let { slot ->
        TimeEditDialog(
            slot = slot,
            onDismiss = { editingSlot = null },
            onConfirm = { startHour, startMinute, endHour, endMinute ->
                val startStr = "%02d:%02d".format(startHour, startMinute)
                val endStr = "%02d:%02d".format(endHour, endMinute)
                editedSlots = editedSlots.map {
                    if (it.index == slot.index) it.copy(startTime = startStr, endTime = endStr)
                    else it
                }
                editingSlot = null
            },
        )
    }
}

@Composable
private fun TimeSlotCard(
    slot: ClassTimeSlot,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "第 ${slot.index} 节",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slot.startTime,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = " — ",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = slot.endTime,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeEditDialog(
    slot: ClassTimeSlot,
    onDismiss: () -> Unit,
    onConfirm: (startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) -> Unit,
) {
    val startParts = slot.startTime.split(":").map { it.toIntOrNull() ?: 0 }
    val endParts = slot.endTime.split(":").map { it.toIntOrNull() ?: 0 }

    var editingStart by remember { mutableStateOf(true) }
    var startHour by remember { mutableStateOf(startParts.getOrElse(0) { 8 }) }
    var startMinute by remember { mutableStateOf(startParts.getOrElse(1) { 0 }) }
    var endHour by remember { mutableStateOf(endParts.getOrElse(0) { 8 }) }
    var endMinute by remember { mutableStateOf(endParts.getOrElse(1) { 45 }) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val pickerState = rememberTimePickerState(
        initialHour = startHour,
        initialMinute = startMinute,
        is24Hour = true,
    )

    fun selectTarget(start: Boolean) {
        if (editingStart) {
            startHour = pickerState.hour
            startMinute = pickerState.minute
        } else {
            endHour = pickerState.hour
            endMinute = pickerState.minute
        }
        editingStart = start
        pickerState.hour = if (start) startHour else endHour
        pickerState.minute = if (start) startMinute else endMinute
        validationError = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑第 ${slot.index} 节时间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TabRow(selectedTabIndex = if (editingStart) 0 else 1) {
                    Tab(
                        selected = editingStart,
                        onClick = { selectTarget(true) },
                        text = { Text("开始 %02d:%02d".format(startHour, startMinute)) },
                    )
                    Tab(
                        selected = !editingStart,
                        onClick = { selectTarget(false) },
                        text = { Text("结束 %02d:%02d".format(endHour, endMinute)) },
                    )
                }
                TimePicker(state = pickerState)
                validationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (editingStart) {
                    startHour = pickerState.hour
                    startMinute = pickerState.minute
                } else {
                    endHour = pickerState.hour
                    endMinute = pickerState.minute
                }
                val startTotal = startHour * 60 + startMinute
                val endTotal = endHour * 60 + endMinute
                if (endTotal <= startTotal) {
                    validationError = "结束时间必须晚于开始时间"
                } else {
                    onConfirm(startHour, startMinute, endHour, endMinute)
                }
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Preview(name = "上课时间设置")
@Composable
private fun ClassTimeSettingsRoutePreview() {
    CampusTheme {
        ClassTimeSettingsRoute(
            slots = DefaultClassTimeSlots,
            season = ClassTimeSeason.SUMMER,
            maxUsedSlot = 0,
            onSave = {},
            onSeasonSelected = {},
            onBack = {},
        )
    }
}
