package com.shisan.campuspro.core.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorUpdatePackageDownloaderTest {
    @Test
    fun `download streams bytes and reports completion`() = runTest {
        val bytes = "apk-stream-content".encodeToByteArray()
        val client = HttpClient(MockEngine {
            respond(
                content = ByteReadChannel(bytes),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, bytes.size.toString()),
            )
        })
        val directory = kotlin.io.path.createTempDirectory().toFile().apply { deleteOnExit() }
        val progress = mutableListOf<Int?>()
        val manifest = UpdateManifest(
            schemaVersion = 1,
            packageName = "com.shisan.campuspro",
            versionCode = 2,
            versionName = "1.1.0",
            apkUrl = "https://update.example.com/app.apk",
            apkSizeBytes = bytes.size.toLong(),
            sha256 = "a".repeat(64),
            publishedAt = "2026-07-10T12:00:00Z",
            releaseNotes = listOf("test"),
        )

        val file = KtorUpdatePackageDownloader(client, directory, allowLocalhostHttp = false)
            .download(manifest) { progress += it }

        assertArrayEquals(bytes, file.readBytes())
        assertEquals(100, progress.last())
        file.delete()
        directory.delete()
    }

    @Test
    fun `download without content length reports indeterminate progress`() = runTest {
        val bytes = "unknown-length".encodeToByteArray()
        val client = HttpClient(MockEngine {
            respond(ByteReadChannel(bytes), HttpStatusCode.OK)
        })
        val directory = kotlin.io.path.createTempDirectory().toFile()
        val progress = mutableListOf<Int?>()

        KtorUpdatePackageDownloader(client, directory, allowLocalhostHttp = false)
            .download(manifest(bytes.size.toLong())) { progress += it }

        assertNull(progress.first())
        assertEquals(100, progress.last())
        directory.deleteRecursively()
    }

    @Test
    fun `download failure removes partial and final files`() = runTest {
        val client = HttpClient(MockEngine {
            respond("failed", HttpStatusCode.InternalServerError)
        })
        val directory = kotlin.io.path.createTempDirectory().toFile()

        val failure = runCatching {
            KtorUpdatePackageDownloader(client, directory, allowLocalhostHttp = false)
                .download(manifest(10)) { }
        }

        assertTrue(failure.isFailure)
        assertFalse(directory.listFiles().orEmpty().any { it.extension == "apk" || it.extension == "part" })
        directory.deleteRecursively()
    }

    @Test
    fun `interrupted response removes partial file`() = runTest {
        val client = HttpClient(MockEngine {
            val channel = ByteChannel(autoFlush = true)
            channel.writeFully("partial-apk".encodeToByteArray())
            channel.cancel(IOException("connection reset"))
            respond(
                content = channel,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, "100"),
            )
        })
        val directory = kotlin.io.path.createTempDirectory().toFile()

        val failure = runCatching {
            KtorUpdatePackageDownloader(client, directory, allowLocalhostHttp = false)
                .download(manifest(100)) { }
        }

        assertTrue(failure.isFailure)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        directory.deleteRecursively()
    }

    private fun manifest(size: Long) = UpdateManifest(
        schemaVersion = 1,
        packageName = "com.shisan.campuspro",
        versionCode = 2,
        versionName = "1.1.0",
        apkUrl = "https://update.example.com/app.apk",
        apkSizeBytes = size,
        sha256 = "a".repeat(64),
        publishedAt = "2026-07-10T12:00:00Z",
        releaseNotes = listOf("test"),
    )
}
