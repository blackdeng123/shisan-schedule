package com.shisan.campuspro.feature.settings

import com.shisan.campuspro.core.update.AppUpdateState
import com.shisan.campuspro.core.update.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateStatusTextTest {
    private val manifest = UpdateManifest(
        schemaVersion = 1,
        packageName = "com.shisan.campuspro",
        versionCode = 2,
        versionName = "1.1.0",
        apkUrl = "https://update.example.com/releases/app.apk",
        apkSizeBytes = 1024,
        sha256 = "a".repeat(64),
        publishedAt = "2026-07-10T12:00:00Z",
        releaseNotes = listOf("测试"),
    )

    @Test
    fun `version text uses build supplied value`() {
        assertEquals("闪电课表 v1.2.3-test", settingsVersionText("1.2.3-test"))
    }

    @Test
    fun `maps every update state to user text`() {
        assertEquals("未配置更新服务", settingsUpdateStatusText(AppUpdateState.Idle, false))
        assertEquals("检查是否有新版本", settingsUpdateStatusText(AppUpdateState.Idle, true))
        assertEquals("正在检查", settingsUpdateStatusText(AppUpdateState.Checking, true))
        assertEquals("已是最新版本", settingsUpdateStatusText(AppUpdateState.UpToDate, true))
        assertEquals("发现新版本 1.1.0", settingsUpdateStatusText(AppUpdateState.Available(manifest), true))
        assertEquals("正在下载", settingsUpdateStatusText(AppUpdateState.Downloading(manifest, null), true))
        assertEquals("正在下载 42%", settingsUpdateStatusText(AppUpdateState.Downloading(manifest, 42), true))
        assertEquals("下载完成，等待安装", settingsUpdateStatusText(AppUpdateState.Downloaded(manifest), true))
        assertEquals("网络错误", settingsUpdateStatusText(AppUpdateState.Error("网络错误"), true))
    }
}
