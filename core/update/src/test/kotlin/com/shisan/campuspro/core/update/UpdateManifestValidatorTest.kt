package com.shisan.campuspro.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestValidatorTest {
    private val validManifest = UpdateManifest(
        schemaVersion = 1,
        packageName = "com.shisan.campuspro",
        versionCode = 2,
        versionName = "1.1.0",
        apkUrl = "https://update.example.com/releases/campuspro-1.1.0.apk",
        apkSizeBytes = 30L * 1024 * 1024,
        sha256 = "a".repeat(64),
        publishedAt = "2026-07-10T12:00:00Z",
        releaseNotes = listOf("修复课表同步"),
    )

    @Test
    fun `valid production manifest is accepted`() {
        val result = UpdateManifestValidator.validate(
            manifest = validManifest,
            currentPackageName = "com.shisan.campuspro",
            currentVersionCode = 1,
            allowLocalhostHttp = false,
        )

        assertTrue(result is ManifestValidationResult.UpdateAvailable)
    }

    @Test
    fun `same or older version is up to date`() {
        val result = UpdateManifestValidator.validate(
            manifest = validManifest.copy(versionCode = 1),
            currentPackageName = "com.shisan.campuspro",
            currentVersionCode = 1,
            allowLocalhostHttp = false,
        )

        assertEquals(ManifestValidationResult.UpToDate, result)
    }

    @Test
    fun `production rejects non https apk`() {
        val result = UpdateManifestValidator.validate(
            manifest = validManifest.copy(apkUrl = "http://update.example.com/app.apk"),
            currentPackageName = "com.shisan.campuspro",
            currentVersionCode = 1,
            allowLocalhostHttp = false,
        )

        assertTrue(result is ManifestValidationResult.Invalid)
    }

    @Test
    fun `debug accepts localhost http only`() {
        val accepted = UpdateManifestValidator.validate(
            manifest = validManifest.copy(apkUrl = "http://127.0.0.1:8080/app.apk"),
            currentPackageName = "com.shisan.campuspro",
            currentVersionCode = 1,
            allowLocalhostHttp = true,
        )
        val rejected = UpdateManifestValidator.validate(
            manifest = validManifest.copy(apkUrl = "http://192.168.1.2:8080/app.apk"),
            currentPackageName = "com.shisan.campuspro",
            currentVersionCode = 1,
            allowLocalhostHttp = true,
        )

        assertTrue(accepted is ManifestValidationResult.UpdateAvailable)
        assertTrue(rejected is ManifestValidationResult.Invalid)
    }

    @Test
    fun `package and sha must match expected format`() {
        val wrongPackage = UpdateManifestValidator.validate(
            manifest = validManifest.copy(packageName = "com.example.other"),
            currentPackageName = "com.shisan.campuspro",
            currentVersionCode = 1,
            allowLocalhostHttp = false,
        )
        val wrongSha = UpdateManifestValidator.validate(
            manifest = validManifest.copy(sha256 = "bad"),
            currentPackageName = "com.shisan.campuspro",
            currentVersionCode = 1,
            allowLocalhostHttp = false,
        )

        assertTrue(wrongPackage is ManifestValidationResult.Invalid)
        assertTrue(wrongSha is ManifestValidationResult.Invalid)
    }
}
