package com.shisan.campuspro.core.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.shisan.campuspro.core.datastore.UserPreferencesDataSource
import java.io.File
import kotlinx.coroutines.flow.first

class DataStoreUpdateCheckStore(
    private val preferences: UserPreferencesDataSource,
) : UpdateCheckStore {
    override suspend fun lastCheckMillis(): Long? = preferences.lastAutoUpdateCheckAtMillis.first()

    override suspend fun setLastCheckMillis(value: Long) {
        preferences.setLastAutoUpdateCheckAtMillis(value)
    }
}

class AndroidUpdatePackageVerifier(
    private val context: Context,
) : UpdatePackageVerifier {
    override fun verify(file: File, manifest: UpdateManifest): Result<Unit> = runCatching {
        UpdateFileIntegrity.verify(file, manifest.apkSizeBytes, manifest.sha256).getOrThrow()
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.path, 0)
        }
        requireNotNull(packageInfo) { "无法读取更新包信息" }
        require(packageInfo.packageName == manifest.packageName) { "更新包名不匹配" }
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        require(archiveVersionCode == manifest.versionCode) { "更新包版本号不匹配" }
    }
}

class AndroidInstallRequestFactory(
    private val context: Context,
    private val authority: String,
) : InstallRequestFactory {
    override fun create(file: File): InstallRequest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            return InstallRequest.RequestPermission(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("update-apk", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return InstallRequest.OpenInstaller(intent)
    }
}

object UpdateCacheCleaner {
    private const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun clean(directory: File, nowMillis: Long = System.currentTimeMillis()) {
        directory.listFiles()?.forEach { file ->
            if (nowMillis - file.lastModified() >= MAX_AGE_MILLIS) file.delete()
        }
    }
}
