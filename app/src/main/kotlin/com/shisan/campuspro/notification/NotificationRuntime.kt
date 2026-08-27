package com.shisan.campuspro.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shisan.campuspro.CampusApplication
import com.shisan.campuspro.MainActivity
import com.shisan.campuspro.R
import com.shisan.campuspro.core.data.AcademicUpdateNotifier
import com.shisan.campuspro.core.data.ScheduleRepository
import com.shisan.campuspro.core.datastore.UserPreferencesDataSource
import com.shisan.campuspro.core.model.AcademicUpdateDiff
import com.shisan.campuspro.core.model.SyncType
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

const val DESTINATION_EXTRA = "notification_destination"
const val DESTINATION_SCHEDULE = "schedule"
const val DESTINATION_EXAMS = "exams"
const val DESTINATION_GRADES = "grades"

object CampusNotificationChannels {
    data class Spec(val id: String, val name: String, val importance: Int)

    const val COURSE = "course_reminders_heads_up_v2"
    const val ACADEMIC = "academic_updates_heads_up_v2"
    const val notificationPriority = NotificationCompat.PRIORITY_HIGH

    fun specs(): List<Spec> = listOf(
        Spec(COURSE, "课程提醒", NotificationManager.IMPORTANCE_HIGH),
        Spec(ACADEMIC, "教务更新", NotificationManager.IMPORTANCE_HIGH),
    )

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(specs().map { spec ->
            NotificationChannel(spec.id, spec.name, spec.importance).apply {
                enableVibration(true)
            }
        })
        manager.deleteNotificationChannel("course_reminders")
        manager.deleteNotificationChannel("academic_updates")
    }
}

object CampusNotificationPresentation {
    val smallIconRes: Int = R.drawable.ic_notification
    const val autoCancel: Boolean = true
    const val ongoing: Boolean = false
    val timeoutAfterMillis: Long? = null
}

class NotificationPublisher(private val context: Context) : AcademicUpdateNotifier {
    override fun canPostNotifications(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun publishCourse(id: Int, courseName: String, location: String) {
        publish(
            id = id,
            channel = CampusNotificationChannels.COURSE,
            title = "即将上课：$courseName",
            message = location.ifBlank { "请查看课表确认上课地点" },
            destination = DESTINATION_SCHEDULE,
        )
    }

    override fun publishExamUpdates(diff: AcademicUpdateDiff) {
        if (!diff.hasChanges) return
        publish(20_001, CampusNotificationChannels.ACADEMIC, "考试安排有更新", diff.summary, DESTINATION_EXAMS)
    }

    override fun publishGradeUpdates(diff: AcademicUpdateDiff) {
        if (!diff.hasChanges) return
        publish(20_002, CampusNotificationChannels.ACADEMIC, "成绩有更新", diff.summary, DESTINATION_GRADES)
    }

    private fun publish(id: Int, channel: String, title: String, message: String, destination: String) {
        if (!canPostNotifications()) return
        CampusNotificationChannels.ensure(context)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(DESTINATION_EXTRA, destination)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            destination.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(CampusNotificationPresentation.smallIconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(CampusNotificationPresentation.autoCancel)
            .setOngoing(CampusNotificationPresentation.ongoing)
            .setPriority(CampusNotificationChannels.notificationPriority)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

class CourseReminderManager(
    private val context: Context,
    private val preferences: UserPreferencesDataSource,
    private val scheduleRepository: ScheduleRepository,
    private val planner: CourseReminderPlanner = CourseReminderPlanner(),
) {
    suspend fun rebuild(now: ZonedDateTime = ZonedDateTime.now()) {
        cancelAll()
        val settings = preferences.notificationSettings.first()
        val publisher = NotificationPublisher(context)
        if (!settings.courseReminderEnabled || !publisher.canPostNotifications()) return
        val schedule = scheduleRepository.observeActiveSchedule().first() ?: return
        val reminders = planner.plan(schedule, now, settings.courseLeadMinutes)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        reminders.forEach { reminder ->
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAt.toInstant().toEpochMilli(),
                pendingIntent(reminder, PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        preferences.setScheduledCourseReminderIds(reminders.map { it.id }.toSet())
    }

    suspend fun cancelAll() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        preferences.scheduledCourseReminderIds.first().forEach { id ->
            val pending = pendingIntent(id, PendingIntent.FLAG_NO_CREATE)
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
        preferences.setScheduledCourseReminderIds(emptySet())
    }

    fun scheduleTest(delaySeconds: Long, title: String = "测试课程提醒") {
        val reminder = CourseReminder(
            id = "debug_test_$delaySeconds",
            courseId = "debug",
            courseName = title,
            location = "通知调试台",
            triggerAt = ZonedDateTime.now().plusSeconds(delaySeconds),
        )
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.triggerAt.toInstant().toEpochMilli(),
            pendingIntent(reminder, PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }

    fun cancelTests() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        listOf(10L, 60L).forEach { seconds ->
            pendingIntent("debug_test_$seconds", PendingIntent.FLAG_NO_CREATE)?.let { pending ->
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
    }

    private fun pendingIntent(reminder: CourseReminder, flags: Int): PendingIntent =
        checkNotNull(pendingIntent(reminder.id, flags, reminder.courseName, reminder.location))

    private fun pendingIntent(id: String, flags: Int): PendingIntent? = pendingIntent(id, flags, "", "")

    private fun pendingIntent(id: String, flags: Int, courseName: String, location: String): PendingIntent? {
        val intent = Intent(context, CourseAlarmReceiver::class.java)
            .setAction("${context.packageName}.COURSE_REMINDER.$id")
            .putExtra(CourseAlarmReceiver.EXTRA_NAME, courseName)
            .putExtra(CourseAlarmReceiver.EXTRA_LOCATION, location)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }
}

class CourseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "即将上课" }
        val location = intent.getStringExtra(EXTRA_LOCATION).orEmpty()
        NotificationPublisher(context).publishCourse(intent.action.hashCode(), name, location)
    }

    companion object {
        const val EXTRA_NAME = "course_name"
        const val EXTRA_LOCATION = "course_location"
    }
}

class CourseReminderRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CampusApplication).appContainer
        container.courseReminderManager.rebuild()
        return Result.success()
    }
}

class AcademicUpdateSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CampusApplication).appContainer
        val settings = container.notificationSettings.first()
        if (!NotificationPublisher(applicationContext).canPostNotifications()) return Result.success()
        return runCatching {
            if (settings.examUpdateEnabled) container.syncRepository.syncByType(SyncType.EXAMS)
            if (settings.gradeUpdateEnabled) container.syncRepository.syncByType(SyncType.GRADES)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

class NotificationWorkScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    fun reconcile(courseEnabled: Boolean, academicEnabled: Boolean, permissionGranted: Boolean) {
        if (courseEnabled && permissionGranted) {
            val refresh = PeriodicWorkRequestBuilder<CourseReminderRefreshWorker>(24, TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork(COURSE_REFRESH, ExistingPeriodicWorkPolicy.UPDATE, refresh)
            requestCourseRebuild()
        } else {
            workManager.cancelUniqueWork(COURSE_REFRESH)
            requestCourseRebuild()
        }
        if (academicEnabled && permissionGranted) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val sync = PeriodicWorkRequestBuilder<AcademicUpdateSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(ACADEMIC_SYNC, ExistingPeriodicWorkPolicy.UPDATE, sync)
        } else {
            workManager.cancelUniqueWork(ACADEMIC_SYNC)
        }
    }

    fun requestCourseRebuild() {
        workManager.enqueueUniqueWork(
            COURSE_REBUILD,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CourseReminderRefreshWorker>().build(),
        )
    }

    fun runAcademicSyncNow() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        workManager.enqueueUniqueWork(
            ACADEMIC_SYNC_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AcademicUpdateSyncWorker>().setConstraints(constraints).build(),
        )
    }

    fun cancelAll() {
        listOf(COURSE_REFRESH, COURSE_REBUILD, ACADEMIC_SYNC, ACADEMIC_SYNC_NOW).forEach(workManager::cancelUniqueWork)
    }

    companion object {
        const val COURSE_REBUILD = "course_reminder_rebuild_v1"
        const val COURSE_REFRESH = "course_reminder_refresh_v1"
        const val ACADEMIC_SYNC = "academic_update_sync_v1"
        const val ACADEMIC_SYNC_NOW = "academic_update_sync_now_v1"
    }
}

class NotificationSystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationWorkScheduler(context).requestCourseRebuild()
    }
}
