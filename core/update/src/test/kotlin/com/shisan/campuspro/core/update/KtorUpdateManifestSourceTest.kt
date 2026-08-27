package com.shisan.campuspro.core.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorUpdateManifestSourceTest {
    @Test
    fun `fetch decodes valid manifest`() = runTest {
        val client = testClient(
            status = HttpStatusCode.OK,
            body = """
                {
                  "schemaVersion": 1,
                  "packageName": "com.shisan.campuspro",
                  "versionCode": 2,
                  "versionName": "1.1.0",
                  "apkUrl": "https://update.example.com/releases/app.apk",
                  "apkSizeBytes": 1024,
                  "sha256": "${"a".repeat(64)}",
                  "publishedAt": "2026-07-10T12:00:00Z",
                  "releaseNotes": ["修复问题"]
                }
            """.trimIndent(),
        )

        val manifest = KtorUpdateManifestSource(client).fetch("https://update.example.com/latest.json")

        assertEquals(2, manifest.versionCode)
        assertEquals("1.1.0", manifest.versionName)
    }

    @Test
    fun `fetch rejects non successful status`() = runTest {
        val source = KtorUpdateManifestSource(testClient(HttpStatusCode.NotFound, "missing"))

        val failure = runCatching { source.fetch("https://update.example.com/latest.json") }

        assertTrue(failure.exceptionOrNull() is UpdateNetworkException)
    }

    @Test
    fun `fetch rejects server error`() = runTest {
        val source = KtorUpdateManifestSource(testClient(HttpStatusCode.InternalServerError, "failed"))

        val failure = runCatching { source.fetch("https://update.example.com/latest.json") }

        assertTrue(failure.exceptionOrNull() is UpdateNetworkException)
    }

    @Test
    fun `fetch propagates network timeout`() = runTest {
        val client = HttpClient(MockEngine { throw SocketTimeoutException("timeout") })

        val failure = runCatching {
            KtorUpdateManifestSource(client).fetch("https://update.example.com/latest.json")
        }

        assertTrue(failure.exceptionOrNull() is SocketTimeoutException)
    }

    private fun testClient(status: HttpStatusCode, body: String): HttpClient = HttpClient(
        MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
    ) {
        install(ContentNegotiation) { json() }
    }
}
