package com.shisan.campuspro.core.update

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import java.io.File
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.Json

class KtorUpdatePackageDownloader(
    private val client: HttpClient,
    private val destinationDirectory: File,
    private val allowLocalhostHttp: Boolean,
) : UpdatePackageDownloader {
    override suspend fun download(manifest: UpdateManifest, onProgress: (Int?) -> Unit): File {
        require(UpdateManifestValidator.isAllowedUrl(manifest.apkUrl, allowLocalhostHttp)) {
            "更新包地址无效"
        }
        destinationDirectory.mkdirs()
        destinationDirectory.listFiles()?.forEach(File::delete)
        val safeVersion = manifest.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(destinationDirectory, "campuspro-$safeVersion.apk")
        val partial = File(destinationDirectory, "${target.name}.part")

        try {
            client.prepareGet(manifest.apkUrl) {
                timeout {
                    requestTimeoutMillis = 10 * 60 * 1000
                    socketTimeoutMillis = 60_000
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw UpdateNetworkException("更新包下载失败：HTTP ${response.status.value}")
                }
                require(
                    UpdateManifestValidator.isAllowedUrl(
                        response.call.request.url.toString(),
                        allowLocalhostHttp,
                    ),
                ) { "更新包发生了不安全的重定向" }
                val total = response.contentLength()?.takeIf { it > 0 }
                val channel = response.bodyAsChannel()
                var downloaded = 0L
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = channel.readAvailable(buffer, 0, buffer.size)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(total?.let { ((downloaded * 100 / it).coerceIn(0, 100)).toInt() })
                    }
                }
                if (total != null && downloaded != total) {
                    throw UpdateNetworkException("更新包下载不完整")
                }
            }
            check(partial.renameTo(target)) { "无法保存更新包" }
            onProgress(100)
            return target
        } catch (error: Throwable) {
            partial.delete()
            target.delete()
            throw error
        }
    }
}

object UpdateHttpClientFactory {
    fun create(): HttpClient = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = false })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        engine {
            config {
                retryOnConnectionFailure(true)
                followRedirects(true)
                followSslRedirects(true)
            }
        }
    }
}
