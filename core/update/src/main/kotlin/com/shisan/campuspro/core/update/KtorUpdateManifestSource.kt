package com.shisan.campuspro.core.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess

class UpdateNetworkException(message: String) : IllegalStateException(message)

class KtorUpdateManifestSource(
    private val client: HttpClient,
) : UpdateManifestSource {
    override suspend fun fetch(url: String): UpdateManifest {
        val response = client.get(url)
        if (!response.status.isSuccess()) {
            throw UpdateNetworkException("更新服务返回 HTTP ${response.status.value}")
        }
        return runCatching { response.body<UpdateManifest>() }
            .getOrElse { throw UpdateNetworkException("更新清单格式错误") }
    }
}
