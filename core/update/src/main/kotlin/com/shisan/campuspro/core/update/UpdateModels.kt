package com.shisan.campuspro.core.update

import android.content.Intent
import kotlinx.serialization.Serializable

@Serializable
data class UpdateManifest(
    val schemaVersion: Int,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val sha256: String,
    val publishedAt: String,
    val releaseNotes: List<String>,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data class Available(val manifest: UpdateManifest) : AppUpdateState
    data class Downloading(val manifest: UpdateManifest, val progress: Int?) : AppUpdateState
    data class Downloaded(val manifest: UpdateManifest) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

sealed interface InstallRequest {
    data class RequestPermission(val intent: Intent) : InstallRequest
    data class OpenInstaller(val intent: Intent) : InstallRequest
    data class Unavailable(val message: String) : InstallRequest
}

sealed interface ManifestValidationResult {
    data object UpToDate : ManifestValidationResult
    data class UpdateAvailable(val manifest: UpdateManifest) : ManifestValidationResult
    data class Invalid(val message: String) : ManifestValidationResult
}
