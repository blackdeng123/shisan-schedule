package com.shisan.campuspro.feature.settings

import com.shisan.campuspro.core.update.AppUpdateState

fun settingsVersionText(versionName: String): String = "闪电课表 v$versionName"

fun settingsUpdateStatusText(
    state: AppUpdateState,
    manifestConfigured: Boolean,
): String = when (state) {
    AppUpdateState.Idle -> if (manifestConfigured) "检查是否有新版本" else "未配置更新服务"
    AppUpdateState.Checking -> "正在检查"
    AppUpdateState.UpToDate -> "已是最新版本"
    is AppUpdateState.Available -> "发现新版本 ${state.manifest.versionName}"
    is AppUpdateState.Downloading -> state.progress?.let { "正在下载 $it%" } ?: "正在下载"
    is AppUpdateState.Downloaded -> "下载完成，等待安装"
    is AppUpdateState.Error -> state.message
}
