package com.shisan.campuspro.core.update

import java.io.File
import kotlinx.coroutines.flow.StateFlow

interface AppUpdateManager {
    val state: StateFlow<AppUpdateState>
    suspend fun checkForUpdate(manual: Boolean)
    suspend fun downloadUpdate()
    fun dismiss()
    fun createInstallRequest(): InstallRequest
}

interface UpdateManifestSource {
    suspend fun fetch(url: String): UpdateManifest
}

interface UpdateCheckStore {
    suspend fun lastCheckMillis(): Long?
    suspend fun setLastCheckMillis(value: Long)
}

interface UpdatePackageDownloader {
    suspend fun download(manifest: UpdateManifest, onProgress: (Int?) -> Unit): File
}

interface UpdatePackageVerifier {
    fun verify(file: File, manifest: UpdateManifest): Result<Unit>
}

interface InstallRequestFactory {
    fun create(file: File): InstallRequest
}
