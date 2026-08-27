package com.shisan.campuspro.notification

import android.app.NotificationManager
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManufacturerNotificationAdvisorTest {
    @Test
    fun `normalizes domestic manufacturer aliases`() {
        val cases = mapOf(
            "Xiaomi" to ManufacturerFamily.XIAOMI,
            "Redmi" to ManufacturerFamily.XIAOMI,
            "HUAWEI" to ManufacturerFamily.HUAWEI,
            "OPPO" to ManufacturerFamily.OPPO,
            "OnePlus" to ManufacturerFamily.OPPO,
            "realme" to ManufacturerFamily.OPPO,
            "vivo" to ManufacturerFamily.VIVO,
            "iQOO" to ManufacturerFamily.VIVO,
            "HONOR" to ManufacturerFamily.HONOR,
        )

        cases.forEach { (manufacturer, expected) ->
            assertEquals(expected, ManufacturerNotificationAdvisor.advise(manufacturer, manufacturer).family)
        }
        assertEquals(
            ManufacturerFamily.STANDARD_ANDROID,
            ManufacturerNotificationAdvisor.advise("Google", "Pixel").family,
        )
    }

    @Test
    fun `provides manufacturer specific risks using public settings entries only`() {
        val xiaomi = ManufacturerNotificationAdvisor.advise("Xiaomi", "Redmi")
        val huawei = ManufacturerNotificationAdvisor.advise("HUAWEI", "HUAWEI")

        assertTrue(xiaomi.riskSummary.contains("后台发送本地通知"))
        assertTrue(huawei.guidance.contains("应用启动管理"))
        assertEquals(
            setOf(
                NotificationSettingsEntry.APP_NOTIFICATIONS,
                NotificationSettingsEntry.APP_DETAILS,
                NotificationSettingsEntry.BATTERY_OPTIMIZATION,
            ),
            xiaomi.settingsEntries,
        )
    }

    @Test
    fun `health evaluation distinguishes blocked attention manual check and healthy`() {
        val standard = ManufacturerNotificationAdvisor.advise("Google", "Pixel")
        val xiaomi = ManufacturerNotificationAdvisor.advise("Xiaomi", "Redmi")

        assertEquals(
            NotificationHealthLevel.BLOCKED,
            NotificationHealthEvaluator.evaluate(false, 4, 4, false, standard).level,
        )
        assertEquals(
            NotificationHealthLevel.NEEDS_ATTENTION,
            NotificationHealthEvaluator.evaluate(true, 3, 4, true, standard).level,
        )
        assertEquals(
            NotificationHealthLevel.MANUAL_CHECK,
            NotificationHealthEvaluator.evaluate(true, 4, 4, false, xiaomi).level,
        )
        assertEquals(
            NotificationHealthLevel.HEALTHY,
            NotificationHealthEvaluator.evaluate(
                true,
                NotificationManager.IMPORTANCE_HIGH,
                NotificationManager.IMPORTANCE_HIGH,
                false,
                standard,
            ).level,
        )
    }

    @Test
    fun `settings entries map only to public Android actions`() {
        assertEquals(
            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            NotificationSystemSettings.actionFor(NotificationSettingsEntry.APP_NOTIFICATIONS),
        )
        assertEquals(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            NotificationSystemSettings.actionFor(NotificationSettingsEntry.APP_DETAILS),
        )
        assertEquals(
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            NotificationSystemSettings.actionFor(NotificationSettingsEntry.BATTERY_OPTIMIZATION),
        )
    }
}
