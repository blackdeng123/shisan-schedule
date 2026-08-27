package com.shisan.campuspro.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shisan.campuspro.core.model.NotificationSettings

data class NotificationDeviceUiState(
    val deviceName: String,
    val androidApi: Int,
    val courseChannelImportance: Int,
    val academicChannelImportance: Int,
    val healthTitle: String,
    val riskSummary: String,
    val guidance: String,
)

internal data class NotificationDeviceSummary(
    val device: String,
    val channels: String,
    val risk: String,
    val guidance: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsRoute(
    settings: NotificationSettings,
    permissionGranted: Boolean,
    batteryOptimized: Boolean,
    deviceInfo: NotificationDeviceUiState,
    onCourseEnabledChange: (Boolean) -> Unit,
    onLeadMinutesChange: (Int) -> Unit,
    onExamEnabledChange: (Boolean) -> Unit,
    onGradeEnabledChange: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("通知提醒") },
                navigationIcon = { androidx.compose.material3.TextButton(onClick = onBack) { Text("返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatusCard(
                    title = if (permissionGranted) "系统通知已授权" else "系统通知未授权",
                    message = if (permissionGranted) "系统可以投递已开启的提醒。" else "业务开关会保留，但授权前不会执行通知后台任务。",
                ) {
                    if (!permissionGranted) Button(onClick = onRequestPermission) { Text("授权通知") }
                }
            }
            item {
                DeviceAdaptationCard(
                    deviceInfo = deviceInfo,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenBatterySettings = onOpenBatterySettings,
                    onOpenAppSettings = onOpenAppSettings,
                )
            }
            item {
                SettingSwitch("课程提醒", "按当前课表在上课前提醒", settings.courseReminderEnabled, onCourseEnabledChange)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("提前时间", fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(5, 10, 15, 20, 30)) { minutes ->
                            FilterChip(
                                selected = settings.courseLeadMinutes == minutes,
                                onClick = { onLeadMinutesChange(minutes) },
                                label = { Text("$minutes 分") },
                            )
                        }
                    }
                }
            }
            item { SettingSwitch("考试更新", "新增考试或时间、地点、类型变化", settings.examUpdateEnabled, onExamEnabledChange) }
            item { SettingSwitch("成绩更新", "只显示摘要，不在通知中展示分数", settings.gradeUpdateEnabled, onGradeEnabledChange) }
            item {
                StatusCard(
                    title = if (batteryOptimized) "后台运行可能受限" else "后台运行未受电池优化限制",
                    message = "后台同步不常驻。划掉最近任务后由系统重新拉起；厂商强行停止后需重新打开应用。",
                ) {
                    OutlinedButton(onClick = onOpenAppSettings) { Text("打开系统设置") }
                }
            }
            item { Text("考试与成绩在有网络时约每 6 小时检查一次，实际时间可能受系统省电策略影响。", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

internal fun notificationImportanceText(importance: Int): String = when (importance) {
    4 -> "高（支持横幅）"
    3 -> "默认"
    2, 1 -> "低"
    0 -> "已关闭"
    else -> "未知（$importance）"
}

internal fun notificationDeviceSummary(deviceInfo: NotificationDeviceUiState) = NotificationDeviceSummary(
    device = "${deviceInfo.deviceName} · Android API ${deviceInfo.androidApi}",
    channels = "课程：${notificationImportanceText(deviceInfo.courseChannelImportance)} · " +
        "教务：${notificationImportanceText(deviceInfo.academicChannelImportance)}",
    risk = deviceInfo.riskSummary,
    guidance = deviceInfo.guidance,
)

@Composable
private fun DeviceAdaptationCard(
    deviceInfo: NotificationDeviceUiState,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val summary = notificationDeviceSummary(deviceInfo)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("设备适配：${deviceInfo.healthTitle}", fontWeight = FontWeight.SemiBold)
            DeviceDetail("设备", summary.device)
            DeviceDetail("通知渠道", summary.channels)
            DeviceDetail("风险提示", summary.risk)
            DeviceDetail("建议操作", summary.guidance)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DeviceActionButton("通知设置", onOpenNotificationSettings) }
                item { DeviceActionButton("电池设置", onOpenBatterySettings) }
                item { DeviceActionButton("应用详情", onOpenAppSettings) }
            }
        }
    }
}

@Composable
private fun DeviceDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DeviceActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(label)
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun StatusCard(title: String, message: String, action: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodySmall)
            action()
        }
    }
}
