package com.shisan.campuspro.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Locale

enum class ManufacturerFamily {
    XIAOMI,
    HUAWEI,
    OPPO,
    VIVO,
    HONOR,
    STANDARD_ANDROID,
}

enum class NotificationSettingsEntry {
    APP_NOTIFICATIONS,
    APP_DETAILS,
    BATTERY_OPTIMIZATION,
}

data class ManufacturerNotificationAdvice(
    val family: ManufacturerFamily,
    val displayName: String,
    val riskSummary: String,
    val guidance: String,
    val settingsEntries: Set<NotificationSettingsEntry> = NotificationSettingsEntry.entries.toSet(),
)

object ManufacturerNotificationAdvisor {
    fun advise(manufacturer: String, brand: String): ManufacturerNotificationAdvice {
        val identity = "$manufacturer $brand".lowercase(Locale.ROOT)
        val family = when {
            listOf("xiaomi", "redmi", "poco").any(identity::contains) -> ManufacturerFamily.XIAOMI
            identity.contains("honor") -> ManufacturerFamily.HONOR
            identity.contains("huawei") -> ManufacturerFamily.HUAWEI
            listOf("oppo", "oneplus", "realme").any(identity::contains) -> ManufacturerFamily.OPPO
            listOf("vivo", "iqoo").any(identity::contains) -> ManufacturerFamily.VIVO
            else -> ManufacturerFamily.STANDARD_ANDROID
        }
        return when (family) {
            ManufacturerFamily.XIAOMI -> ManufacturerNotificationAdvice(
                family,
                "小米 / Redmi / POCO",
                "Xiaomi HyperOS 可能限制后台发送本地通知，该状态无法通过公开 API 读取。",
                "请在系统通知设置中确认横幅，并检查后台发送本地通知与电池策略。",
            )
            ManufacturerFamily.HUAWEI -> ManufacturerNotificationAdvice(
                family,
                "华为",
                "应用启动管理和系统省电策略可能延迟后台同步。",
                "请确认通知类别的横幅、响铃和锁屏通知；必要时检查应用启动管理与后台活动。",
            )
            ManufacturerFamily.OPPO -> ManufacturerNotificationAdvice(
                family,
                "OPPO / 一加 / realme",
                "ColorOS 的省电策略可能延迟 WorkManager 后台同步。",
                "请确认通知横幅，并在应用详情和电池设置中允许必要的后台活动。",
            )
            ManufacturerFamily.VIVO -> ManufacturerNotificationAdvice(
                family,
                "vivo / iQOO",
                "OriginOS 的省电策略可能延迟 WorkManager 后台同步。",
                "请确认通知悬浮展示，并在应用详情和电池设置中允许必要的后台活动。",
            )
            ManufacturerFamily.HONOR -> ManufacturerNotificationAdvice(
                family,
                "荣耀",
                "MagicOS 的省电策略可能延迟 WorkManager 后台同步。",
                "请确认通知类别的横幅，并在应用详情和电池设置中允许必要的后台活动。",
            )
            ManufacturerFamily.STANDARD_ANDROID -> ManufacturerNotificationAdvice(
                family,
                "标准 Android",
                "后台任务执行时间可能受 Android 省电策略影响。",
                "请确认通知横幅；如提醒延迟，可检查应用电池优化设置。",
            )
        }
    }
}

enum class NotificationHealthLevel { BLOCKED, NEEDS_ATTENTION, MANUAL_CHECK, HEALTHY }

data class NotificationHealth(
    val level: NotificationHealthLevel,
    val problems: List<String>,
)

object NotificationHealthEvaluator {
    fun evaluate(
        permissionGranted: Boolean,
        courseChannelImportance: Int,
        academicChannelImportance: Int,
        batteryOptimized: Boolean,
        advice: ManufacturerNotificationAdvice,
    ): NotificationHealth {
        if (!permissionGranted) {
            return NotificationHealth(NotificationHealthLevel.BLOCKED, listOf("系统通知权限未开启"))
        }
        val problems = buildList {
            if (courseChannelImportance < NotificationManager.IMPORTANCE_HIGH) add("课程提醒渠道未设为高重要性")
            if (academicChannelImportance < NotificationManager.IMPORTANCE_HIGH) add("教务更新渠道未设为高重要性")
            if (batteryOptimized) add("应用仍受电池优化限制")
        }
        if (problems.isNotEmpty()) return NotificationHealth(NotificationHealthLevel.NEEDS_ATTENTION, problems)
        if (advice.family == ManufacturerFamily.XIAOMI) {
            return NotificationHealth(
                NotificationHealthLevel.MANUAL_CHECK,
                listOf("后台发送本地通知权限需在 Xiaomi HyperOS 中人工确认"),
            )
        }
        return NotificationHealth(NotificationHealthLevel.HEALTHY, emptyList())
    }
}

object NotificationSystemSettings {
    fun actionFor(entry: NotificationSettingsEntry): String = when (entry) {
        NotificationSettingsEntry.APP_NOTIFICATIONS -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
        NotificationSettingsEntry.APP_DETAILS -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        NotificationSettingsEntry.BATTERY_OPTIMIZATION -> Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
    }

    fun intentFor(context: Context, entry: NotificationSettingsEntry): Intent = when (entry) {
        NotificationSettingsEntry.APP_NOTIFICATIONS -> Intent(actionFor(entry))
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        NotificationSettingsEntry.APP_DETAILS -> Intent(
            actionFor(entry),
            Uri.parse("package:${context.packageName}"),
        )
        NotificationSettingsEntry.BATTERY_OPTIMIZATION -> Intent(actionFor(entry))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun open(context: Context, entry: NotificationSettingsEntry) {
        runCatching { context.startActivity(intentFor(context, entry)) }
            .recoverCatching {
                if (entry != NotificationSettingsEntry.APP_DETAILS) {
                    context.startActivity(intentFor(context, NotificationSettingsEntry.APP_DETAILS))
                }
            }
    }
}

data class NotificationDeviceSnapshot(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val androidApi: Int,
    val courseChannelImportance: Int,
    val academicChannelImportance: Int,
    val advice: ManufacturerNotificationAdvice,
    val health: NotificationHealth,
)

object NotificationDeviceDiagnostics {
    fun capture(
        context: Context,
        permissionGranted: Boolean,
        batteryOptimized: Boolean,
    ): NotificationDeviceSnapshot {
        CampusNotificationChannels.ensure(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val courseImportance = channelImportance(manager, CampusNotificationChannels.COURSE)
        val academicImportance = channelImportance(manager, CampusNotificationChannels.ACADEMIC)
        val advice = ManufacturerNotificationAdvisor.advise(Build.MANUFACTURER, Build.BRAND)
        return NotificationDeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            androidApi = Build.VERSION.SDK_INT,
            courseChannelImportance = courseImportance,
            academicChannelImportance = academicImportance,
            advice = advice,
            health = NotificationHealthEvaluator.evaluate(
                permissionGranted,
                courseImportance,
                academicImportance,
                batteryOptimized,
                advice,
            ),
        )
    }

    private fun channelImportance(manager: NotificationManager, channelId: String): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.getNotificationChannel(channelId)?.importance ?: NotificationManager.IMPORTANCE_NONE
        } else {
            NotificationManager.IMPORTANCE_HIGH
        }
}
