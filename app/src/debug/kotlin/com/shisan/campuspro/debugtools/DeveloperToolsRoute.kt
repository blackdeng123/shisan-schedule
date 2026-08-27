package com.shisan.campuspro.debugtools

import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import com.shisan.campuspro.AndroidAppContainer
import com.shisan.campuspro.BuildConfig
import com.shisan.campuspro.core.data.AcademicUpdateDetector
import com.shisan.campuspro.core.model.Exam
import com.shisan.campuspro.core.model.ExamSource
import com.shisan.campuspro.core.model.Grade
import com.shisan.campuspro.notification.CourseReminderPlanner
import com.shisan.campuspro.notification.NotificationPublisher
import com.shisan.campuspro.notification.NotificationDeviceDiagnostics
import com.shisan.campuspro.notification.NotificationSettingsEntry
import com.shisan.campuspro.notification.NotificationSystemSettings
import com.shisan.campuspro.notification.NotificationWorkScheduler
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperToolsRoute(container: AndroidAppContainer, onBack: () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val releaseIsolationMarker = "vendor_notification_debug"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.notificationSettings.collectAsStateWithLifecycle(initialValue = com.shisan.campuspro.core.model.NotificationSettings())
    val schedule by container.scheduleRepository.observeActiveSchedule().collectAsStateWithLifecycle(initialValue = null)
    val logs = remember { mutableStateListOf<String>() }
    var refresh by remember { mutableIntStateOf(0) }
    var workStatus by remember { mutableStateOf("读取中") }
    var confirmRealSync by remember { mutableStateOf(false) }
    val publisher = remember { NotificationPublisher(context) }
    val batteryOptimized = !context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)
    val deviceDiagnostics = remember(refresh, batteryOptimized) {
        NotificationDeviceDiagnostics.capture(context, publisher.canPostNotifications(), batteryOptimized)
    }
    val planner = remember { CourseReminderPlanner() }
    val reminders = schedule?.let { planner.plan(it, ZonedDateTime.now(), settings.courseLeadMinutes) }.orEmpty()

    fun log(message: String) {
        logs.add(0, "${java.time.LocalTime.now().withNano(0)}  $message")
        while (logs.size > 100) logs.removeLast()
        refresh++
    }

    LaunchedEffect(refresh) {
        workStatus = withContext(Dispatchers.IO) {
            val wm = WorkManager.getInstance(context)
            val course = wm.getWorkInfosForUniqueWork(NotificationWorkScheduler.COURSE_REFRESH).get().firstOrNull()?.state
            val academic = wm.getWorkInfosForUniqueWork(NotificationWorkScheduler.ACADEMIC_SYNC).get().firstOrNull()?.state
            "课程=$course，教务=$academic"
        }
    }

    if (confirmRealSync) {
        AlertDialog(
            onDismissRequest = { confirmRealSync = false },
            title = { Text("执行真实同步？") },
            text = { Text("将访问教务系统并复用生产考试/成绩通知链路。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRealSync = false
                    container.notificationWorkScheduler.runAcademicSyncNow()
                    log("已提交真实考试/成绩同步")
                }) { Text("执行") }
            },
            dismissButton = { TextButton(onClick = { confirmRealSync = false }) { Text("取消") } },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("开发者工具") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DebugCard("状态总览") {
                Text("${BuildConfig.BUILD_TYPE} / ${BuildConfig.APPLICATION_ID}")
                Text("设备：${deviceDiagnostics.manufacturer} / ${deviceDiagnostics.brand} / ${deviceDiagnostics.model} / API ${deviceDiagnostics.androidApi}")
                Text("厂商适配：${deviceDiagnostics.advice.displayName}，健康=${deviceDiagnostics.health.level}")
                Text("通知权限：${publisher.canPostNotifications()}")
                Text("渠道重要性：课程=${deviceDiagnostics.courseChannelImportance}，教务=${deviceDiagnostics.academicChannelImportance}")
                Text("横幅：系统控制 / 需人工确认；电池优化：$batteryOptimized")
                Text("风险：${deviceDiagnostics.advice.riskSummary}")
                Text("课程=${settings.courseReminderEnabled}，考试=${settings.examUpdateEnabled}，成绩=${settings.gradeUpdateEnabled}")
                Text("课表：${schedule?.name ?: "无"}，未来提醒 ${reminders.size} 条")
                Text("下一次：${reminders.firstOrNull()?.triggerAt ?: "无"}")
                Text("Worker：$workStatus")
            } }
            item { DebugCard("通知测试") {
                ActionRow(
                    "横幅与图标测试" to {
                        publisher.publishCourse(91_001, "测试课程", "调试教室")
                        log("notify() 已调用：课程渠道，横幅时长由系统控制")
                    },
                    "考试更新" to { publisher.publishExamUpdates(AcademicUpdateDetector.compareExams(emptyList(), listOf(testExam()))); log("已发送考试更新") },
                )
                ActionRow(
                    "成绩更新" to { publisher.publishGradeUpdates(AcademicUpdateDetector.compareGrades(emptyList(), listOf(testGrade()))); log("已发送脱敏成绩通知") },
                    "10 秒闹钟" to { container.courseReminderManager.scheduleTest(10); log("已安排 10 秒测试闹钟") },
                )
                ActionRow(
                    "1 分钟闹钟" to { container.courseReminderManager.scheduleTest(60); log("已安排 1 分钟测试闹钟") },
                    "取消测试" to {
                        container.courseReminderManager.cancelTests()
                        androidx.core.app.NotificationManagerCompat.from(context).run {
                            cancel(91_001); cancel(20_001); cancel(20_002)
                        }
                        log("已取消测试闹钟和通知")
                    },
                )
                ActionRow(
                    "系统通知设置" to { NotificationSystemSettings.open(context, NotificationSettingsEntry.APP_NOTIFICATIONS) },
                    "系统电池设置" to { NotificationSystemSettings.open(context, NotificationSettingsEntry.BATTERY_OPTIMIZATION) },
                )
            } }
            item { DebugCard("调度与同步") {
                ActionRow(
                    "重建课程提醒" to { container.notificationWorkScheduler.requestCourseRebuild(); log("已提交课程重建") },
                    "刷新状态" to { refresh++; log("已刷新任务状态") },
                )
                Button(onClick = { confirmRealSync = true }) { Text("真实考试/成绩同步") }
            } }
            item { DebugCard("差异模拟（不写数据库）") {
                Button(onClick = {
                    val old = testExam()
                    val diff = AcademicUpdateDetector.compareExams(listOf(old), listOf(old.copy(location = "新考场")))
                    log("考试变更模拟：${diff.summary}")
                }) { Text("模拟考试地点变化") }
                Button(onClick = {
                    val old = testGrade()
                    val diff = AcademicUpdateDetector.compareGrades(listOf(old), listOf(old.copy(score = 90.0, scoreText = "90", gpa = 4.0)))
                    log("成绩修正模拟：${diff.summary}")
                }) { Text("模拟成绩修正") }
            } }
            item { DebugCard("事件日志") {
                if (logs.isEmpty()) Text("暂无事件") else logs.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            } }
        }
    }
}

@Composable
private fun DebugCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ActionRow(vararg actions: Pair<String, () -> Unit>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { (label, action) -> Button(onClick = action, modifier = Modifier.weight(1f)) { Text(label) } }
    }
}

private fun testExam() = Exam("debug-exam", "测试课程", "期末", 2.0, "测试考场", "2026-07-20 09:00", 7, ExamSource.JWXT)
private fun testGrade() = Grade("debug-grade", "测试课程", "必修", 2.0, 80.0, "80", 3.0, "2025-2026-2")
