package com.shisan.campuspro.core.update

import java.net.URI

object UpdateCheckPolicy {
    const val INTERVAL_MILLIS: Long = 24L * 60 * 60 * 1000

    fun shouldCheck(manual: Boolean, nowMillis: Long, lastCheckMillis: Long?): Boolean =
        manual || lastCheckMillis == null || nowMillis - lastCheckMillis >= INTERVAL_MILLIS
}

object UpdateManifestValidator {
    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")

    fun validate(
        manifest: UpdateManifest,
        currentPackageName: String,
        currentVersionCode: Long,
        allowLocalhostHttp: Boolean,
    ): ManifestValidationResult {
        if (manifest.schemaVersion != 1) return ManifestValidationResult.Invalid("不支持的更新清单版本")
        if (manifest.packageName != currentPackageName) return ManifestValidationResult.Invalid("更新包名不匹配")
        if (manifest.versionCode <= currentVersionCode) return ManifestValidationResult.UpToDate
        if (manifest.versionName.isBlank()) return ManifestValidationResult.Invalid("更新版本名称为空")
        if (manifest.apkSizeBytes <= 0) return ManifestValidationResult.Invalid("更新包大小无效")
        if (!sha256Pattern.matches(manifest.sha256)) return ManifestValidationResult.Invalid("更新包校验值无效")
        if (manifest.releaseNotes.isEmpty()) return ManifestValidationResult.Invalid("更新说明为空")
        if (!isAllowedUrl(manifest.apkUrl, allowLocalhostHttp)) {
            return ManifestValidationResult.Invalid("更新地址必须使用 HTTPS")
        }
        return ManifestValidationResult.UpdateAvailable(manifest)
    }

    fun isAllowedUrl(url: String, allowLocalhostHttp: Boolean): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) return true
        return allowLocalhostHttp &&
            uri.scheme.equals("http", ignoreCase = true) &&
            uri.host == "127.0.0.1"
    }
}
