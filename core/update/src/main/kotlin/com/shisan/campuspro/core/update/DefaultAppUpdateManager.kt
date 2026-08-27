package com.shisan.campuspro.core.update

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultAppUpdateManager(
    private val manifestUrl: String,
    private val currentPackageName: String,
    private val currentVersionCode: Long,
    private val allowLocalhostHttp: Boolean,
    private val autoCheckEnabled: Boolean,
    private val manifestSource: UpdateManifestSource,
    private val checkStore: UpdateCheckStore,
    private val packageDownloader: UpdatePackageDownloader,
    private val packageVerifier: UpdatePackageVerifier,
    private val installRequestFactory: InstallRequestFactory,
    private val clock: () -> Long = System::currentTimeMillis,
) : AppUpdateManager {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    private var downloadedFile: File? = null

    override val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    override suspend fun checkForUpdate(manual: Boolean) = mutex.withLock {
        if (!manual && !autoCheckEnabled) return@withLock
        if (manifestUrl.isBlank()) {
            mutableState.value = if (manual) AppUpdateState.Error("未配置更新服务") else AppUpdateState.Idle
            return@withLock
        }
        if (!UpdateManifestValidator.isAllowedUrl(manifestUrl, allowLocalhostHttp)) {
            mutableState.value = if (manual) AppUpdateState.Error("更新服务地址无效") else AppUpdateState.Idle
            return@withLock
        }

        val now = clock()
        if (!UpdateCheckPolicy.shouldCheck(manual, now, checkStore.lastCheckMillis())) {
            mutableState.value = AppUpdateState.Idle
            return@withLock
        }
        checkStore.setLastCheckMillis(now)
        mutableState.value = AppUpdateState.Checking

        val result = runCatching { manifestSource.fetch(manifestUrl) }
        result.fold(
            onSuccess = { manifest ->
                mutableState.value = when (
                    val validation = UpdateManifestValidator.validate(
                        manifest = manifest,
                        currentPackageName = currentPackageName,
                        currentVersionCode = currentVersionCode,
                        allowLocalhostHttp = allowLocalhostHttp,
                    )
                ) {
                    ManifestValidationResult.UpToDate -> AppUpdateState.UpToDate
                    is ManifestValidationResult.UpdateAvailable -> AppUpdateState.Available(validation.manifest)
                    is ManifestValidationResult.Invalid -> if (manual) {
                        AppUpdateState.Error(validation.message)
                    } else {
                        AppUpdateState.Idle
                    }
                }
            },
            onFailure = { error ->
                mutableState.value = if (manual) {
                    AppUpdateState.Error(error.message ?: "检查更新失败")
                } else {
                    AppUpdateState.Idle
                }
            },
        )
    }

    override suspend fun downloadUpdate() = mutex.withLock {
        val available = mutableState.value as? AppUpdateState.Available ?: return@withLock
        downloadedFile?.delete()
        downloadedFile = null
        mutableState.value = AppUpdateState.Downloading(available.manifest, null)
        var file: File? = null
        runCatching {
            file = packageDownloader.download(available.manifest) { progress ->
                mutableState.value = AppUpdateState.Downloading(available.manifest, progress)
            }
            packageVerifier.verify(requireNotNull(file), available.manifest).getOrThrow()
            downloadedFile = file
            mutableState.value = AppUpdateState.Downloaded(available.manifest)
        }.onFailure { error ->
            file?.delete()
            if (error is CancellationException) {
                mutableState.value = AppUpdateState.Available(available.manifest)
                throw error
            }
            mutableState.value = AppUpdateState.Error(error.message ?: "下载更新失败")
        }
    }

    override fun dismiss() {
        if (mutableState.value !is AppUpdateState.Downloading) mutableState.value = AppUpdateState.Idle
    }

    override fun createInstallRequest(): InstallRequest {
        val file = downloadedFile
        return if (file != null && file.isFile) {
            installRequestFactory.create(file)
        } else {
            InstallRequest.Unavailable("更新包尚未下载完成")
        }
    }
}
