package com.shisan.campuspro.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSettingsFormattingTest {
    @Test
    fun `formats Android channel importance for users`() {
        assertEquals("高（支持横幅）", notificationImportanceText(4))
        assertEquals("默认", notificationImportanceText(3))
        assertEquals("低", notificationImportanceText(2))
        assertEquals("已关闭", notificationImportanceText(0))
        assertEquals("未知（-1000）", notificationImportanceText(-1000))
    }

    @Test
    fun `groups device adaptation details for scanning`() {
        val summary = notificationDeviceSummary(
            NotificationDeviceUiState(
                deviceName = "小米 / Redmi / POCO · MI 8",
                androidApi = 35,
                courseChannelImportance = 4,
                academicChannelImportance = 4,
                healthTitle = "需要调整",
                riskSummary = "Xiaomi HyperOS 可能限制后台发送本地通知。",
                guidance = "请确认横幅，并检查后台发送本地通知与电池策略。",
            ),
        )

        assertEquals("小米 / Redmi / POCO · MI 8 · Android API 35", summary.device)
        assertEquals("课程：高（支持横幅） · 教务：高（支持横幅）", summary.channels)
        assertEquals("Xiaomi HyperOS 可能限制后台发送本地通知。", summary.risk)
        assertEquals("请确认横幅，并检查后台发送本地通知与电池策略。", summary.guidance)
    }
}
