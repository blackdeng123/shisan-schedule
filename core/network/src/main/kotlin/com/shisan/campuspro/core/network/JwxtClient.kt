package com.shisan.campuspro.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.Parameters
import io.ktor.http.Url

data class JwxtResponse(
    val ok: Boolean,
    val status: Int,
    val text: String,
    val url: String,
)

/**
 * 可重置的 Cookie 存储包装器。
 * 所有 HttpClient 实例持有此包装器的引用，调用 [reset] 即清空所有 Cookie，
 * 无需重建 HttpClient。
 */
class ResettableCookieStorage : CookiesStorage {
    @Volatile
    private var delegate: CookiesStorage = AcceptAllCookiesStorage()

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        delegate.addCookie(requestUrl, cookie)
    }

    override suspend fun get(requestUrl: Url): List<Cookie> {
        return delegate.get(requestUrl)
    }

    override fun close() {
        delegate.close()
    }

    /** 清空所有已存储的 Cookie */
    fun reset() {
        delegate = AcceptAllCookiesStorage()
    }
}

class JwxtClient(
    private val baseUrl: String = HttpClientFactory.JWXT_BASE_URL,
    private val client: HttpClient = HttpClientFactory.jwxt(baseUrl),
) {
    suspend fun get(path: String): JwxtResponse {
        val response = client.get(baseUrl + path)
        return JwxtResponse(
            ok = response.status.value == 200,
            status = response.status.value,
            text = response.bodyAsText(),
            url = response.call.request.url.toString(),
        )
    }

    suspend fun post(path: String, form: Map<String, String>): JwxtResponse {
        val response = client.submitForm(
            url = baseUrl + path,
            formParameters = Parameters.build {
                form.forEach { (key, value) -> append(key, value) }
            },
        )
        return JwxtResponse(
            ok = response.status.value == 200,
            status = response.status.value,
            text = response.bodyAsText(),
            url = response.call.request.url.toString(),
        )
    }

    companion object {
        /** 共享 Cookie 存储，供默认客户端和 OAuth 客户端共用 */
        val sharedCookieStorage = ResettableCookieStorage()
    }
}
