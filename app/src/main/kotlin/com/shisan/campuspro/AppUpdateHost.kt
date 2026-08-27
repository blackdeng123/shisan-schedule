package com.shisan.campuspro

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.shisan.campuspro.core.update.AppUpdateManager
import com.shisan.campuspro.core.update.AppUpdateState
import com.shisan.campuspro.core.update.InstallRequest
import com.shisan.campuspro.core.update.UpdateManifest
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun AppUpdateHost(
    manager: AppUpdateManager,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state by manager.state.collectAsStateWithLifecycle()
    val hostViewModel: AppUpdateHostViewModel = viewModel(
        factory = remember(manager) { AppUpdateHostViewModel.Factory(manager) },
    )
    var installMessage by remember { mutableStateOf<String?>(null) }
    val installerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        when (val request = manager.createInstallRequest()) {
            is InstallRequest.OpenInstaller -> {
                installMessage = null
                installerLauncher.launch(request.intent.withoutNewTaskFlag())
            }
            is InstallRequest.RequestPermission -> {
                installMessage = "需要允许此应用安装未知应用后才能继续"
            }
            is InstallRequest.Unavailable -> installMessage = request.message
        }
    }

    fun requestInstall() {
        when (val request = manager.createInstallRequest()) {
            is InstallRequest.OpenInstaller -> {
                installMessage = null
                installerLauncher.launch(request.intent.withoutNewTaskFlag())
            }
            is InstallRequest.RequestPermission -> permissionLauncher.launch(request.intent.withoutNewTaskFlag())
            is InstallRequest.Unavailable -> installMessage = request.message
        }
    }

    LaunchedEffect(manager, enabled) {
        if (enabled) manager.checkForUpdate(manual = false)
    }

    content()

    if (!enabled) return

    when (val current = state) {
        is AppUpdateState.Available -> UpdateDialog(
            manifest = current.manifest,
            progress = null,
            downloading = false,
            downloaded = false,
            installMessage = null,
            onDismiss = manager::dismiss,
            onPrimary = hostViewModel::startDownload,
        )
        is AppUpdateState.Downloading -> UpdateDialog(
            manifest = current.manifest,
            progress = current.progress,
            downloading = true,
            downloaded = false,
            installMessage = null,
            onDismiss = hostViewModel::cancelDownload,
            onPrimary = {},
        )
        is AppUpdateState.Downloaded -> UpdateDialog(
            manifest = current.manifest,
            progress = 100,
            downloading = false,
            downloaded = true,
            installMessage = installMessage,
            onDismiss = manager::dismiss,
            onPrimary = ::requestInstall,
        )
        else -> Unit
    }
}

private class AppUpdateHostViewModel(
    private val manager: AppUpdateManager,
) : ViewModel() {
    private var downloadJob: Job? = null

    fun startDownload() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch { manager.downloadUpdate() }
    }

    fun cancelDownload() {
        val running = downloadJob ?: return
        running.cancel()
        viewModelScope.launch {
            running.join()
            manager.dismiss()
        }
    }

    class Factory(
        private val manager: AppUpdateManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppUpdateHostViewModel(manager) as T
    }
}

@Composable
private fun UpdateDialog(
    manifest: UpdateManifest,
    progress: Int?,
    downloading: Boolean,
    downloaded: Boolean,
    installMessage: String?,
    onDismiss: () -> Unit,
    onPrimary: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 ${manifest.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("发布时间：${manifest.publishedAt}")
                Text("安装包：${formatFileSize(manifest.apkSizeBytes)}")
                manifest.releaseNotes.forEach { note -> Text("• $note") }
                Spacer(Modifier.height(4.dp))
                if (downloading) {
                    DownloadProgress(progress)
                }
                installMessage?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onPrimary, enabled = !downloading) {
                Text(if (downloaded) "安装" else if (downloading) "下载中" else "下载更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (downloading) "取消" else "稍后") }
        },
    )
}

@Composable
private fun DownloadProgress(progress: Int?) {
    val normalizedProgress = progress?.coerceIn(0, 100)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (normalizedProgress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { normalizedProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = when {
                normalizedProgress == null -> "正在连接下载服务"
                normalizedProgress >= 100 -> "下载完成，正在校验安装包"
                else -> "已下载 $normalizedProgress%"
            },
        )
    }
}

private fun formatFileSize(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)

private fun Intent.withoutNewTaskFlag(): Intent = apply {
    flags = flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
}
