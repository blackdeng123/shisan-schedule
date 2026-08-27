package com.shisan.campuspro.core.update

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAppUpdateManagerTest {
    private val manifest = UpdateManifest(
        schemaVersion = 1,
        packageName = "com.shisan.campuspro",
        versionCode = 2,
        versionName = "1.1.0",
        apkUrl = "https://update.example.com/app.apk",
        apkSizeBytes = 1024,
        sha256 = "a".repeat(64),
        publishedAt = "2026-07-10T12:00:00Z",
        releaseNotes = listOf("修复问题"),
    )

    @Test
    fun `automatic check is throttled for twenty four hours`() = runTest {
        val source = FakeManifestSource(manifest)
        val store = FakeUpdateCheckStore(lastCheck = 1000)
        val manager = manager(source = source, store = store, nowMillis = 2000)

        manager.checkForUpdate(manual = false)

        assertEquals(0, source.fetchCount)
        assertEquals(AppUpdateState.Idle, manager.state.value)
    }

    @Test
    fun `manual check bypasses throttle and reports available update`() = runTest {
        val source = FakeManifestSource(manifest)
        val manager = manager(source = source, store = FakeUpdateCheckStore(1000), nowMillis = 2000)

        manager.checkForUpdate(manual = true)

        assertEquals(1, source.fetchCount)
        assertTrue(manager.state.value is AppUpdateState.Available)
    }

    @Test
    fun `automatic failure stays silent while manual failure is visible`() = runTest {
        val source = FakeManifestSource(failure = IllegalStateException("offline"))
        val store = FakeUpdateCheckStore(null)
        val manager = manager(source = source, store = store, nowMillis = 2000)

        manager.checkForUpdate(manual = false)
        assertEquals(AppUpdateState.Idle, manager.state.value)

        manager.checkForUpdate(manual = true)
        assertTrue(manager.state.value is AppUpdateState.Error)
    }

    private fun manager(
        source: UpdateManifestSource,
        store: UpdateCheckStore,
        nowMillis: Long,
    ) = DefaultAppUpdateManager(
        manifestUrl = "https://update.example.com/latest.json",
        currentPackageName = "com.shisan.campuspro",
        currentVersionCode = 1,
        allowLocalhostHttp = false,
        autoCheckEnabled = true,
        manifestSource = source,
        checkStore = store,
        packageDownloader = object : UpdatePackageDownloader {
            override suspend fun download(manifest: UpdateManifest, onProgress: (Int?) -> Unit): File = error("unused")
        },
        packageVerifier = object : UpdatePackageVerifier {
            override fun verify(file: File, manifest: UpdateManifest): Result<Unit> = Result.success(Unit)
        },
        installRequestFactory = object : InstallRequestFactory {
            override fun create(file: File): InstallRequest = InstallRequest.Unavailable("unused")
        },
        clock = { nowMillis },
    )
}

private class FakeManifestSource(
    private val manifest: UpdateManifest? = null,
    private val failure: Throwable? = null,
) : UpdateManifestSource {
    var fetchCount = 0

    override suspend fun fetch(url: String): UpdateManifest {
        fetchCount++
        failure?.let { throw it }
        return requireNotNull(manifest)
    }
}

private class FakeUpdateCheckStore(lastCheck: Long?) : UpdateCheckStore {
    private var value = lastCheck
    override suspend fun lastCheckMillis(): Long? = value
    override suspend fun setLastCheckMillis(value: Long) {
        this.value = value
    }
}
