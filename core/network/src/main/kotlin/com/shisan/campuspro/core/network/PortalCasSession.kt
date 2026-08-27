package com.shisan.campuspro.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.coroutines.delay

data class PortalLoginResponse(
    val castgc: String,
    val kicked: Boolean,
)

class PortalCasSession(
    private val authClient: HttpClient = HttpClientFactory.casAuth(),
    private val jwxtClientFactory: (String) -> JwxtClient = { castgc ->
        JwxtClient(client = HttpClientFactory.jwxtOAuth(castgc))
    },
) {
    suspend fun login(username: String, password: String): Result<PortalLoginResponse> = runCatching {
        // 清空上一次登录残留的 authserver 会话 Cookie，
        // 否则退出后重新登录时登录页可能返回缺少 execution 的页面
        HttpClientFactory.casAuthCookieStorage.reset()
        val loginPageHtml = fetchCasLoginPage()
        val salt = PortalHtmlExtractor.casPasswordSalt(loginPageHtml)
            ?: error("门户登录页缺少 pwdEncryptSalt")
        val execution = PortalHtmlExtractor.casExecution(loginPageHtml)
            ?: error("门户登录页缺少 execution")
        val lt = PortalHtmlExtractor.inputValueById(loginPageHtml, "lt").orEmpty()
        val encryptedPassword = PortalCasCrypto.encryptPassword(password, salt)

        val response = authClient.submitForm(
            url = LOGIN_URL,
            formParameters = Parameters.build {
                append("username", username)
                append("password", encryptedPassword)
                append("captcha", "")
                append("rememberMe", "true")
                append("_eventId", "submit")
                append("cllt", "userNameLogin")
                append("dllt", "generalLogin")
                append("lt", lt)
                append("execution", execution)
            },
        ) {
            header(HttpHeaders.Origin, AUTH_ORIGIN)
            header(HttpHeaders.Referrer, LOGIN_URL)
        }

        if (response.status.isRedirect()) {
            val castgc = PortalHtmlExtractor.castgcFromSetCookie(response.headers.getAll(HttpHeaders.SetCookie).orEmpty())
                ?: error("门户登录成功但未返回 CASTGC")
            val jwxtClient = jwxtClientFactory(castgc)
            val oauthResponse = jwxtClient.get("/login/oauth2")
            if (!oauthResponse.ok) error("OAuth2 桥接失败，状态码 ${oauthResponse.status}")
            if (!oauthResponse.url.startsWith("https://jwxt.jsu.edu.cn"))
                error("OAuth2 桥接失败：最终页面不在教务系统域 (${oauthResponse.url})")
            if (oauthResponse.text.contains("统一身份认证", ignoreCase = true))
                error("OAuth2 桥接失败：获取到统一认证页面而非教务系统页面")
            return@runCatching PortalLoginResponse(
                castgc = castgc,
                kicked = false,
            )
        }

        val body = response.bodyAsText()
        if (body.contains("踢出会话")) {
            return@runCatching continueAfterKick(body, username)
        }

        throw IllegalStateException(PortalHtmlExtractor.errorMessage(body) ?: "门户认证失败")
    }

    /**
     * 获取 CAS 登录页 HTML。若首次请求返回的页面缺少 execution 参数
     *（可能是 CAS 服务端会话状态未就绪），重置 Cookie 后短暂等待并重试一次。
     */
    private suspend fun fetchCasLoginPage(): String {
        val html = authClient.get(LOGIN_URL).bodyAsText()
        if (PortalHtmlExtractor.casExecution(html) != null) return html
        // 首次请求可能携带异常会话状态，重置后重试以获得全新登录页
        HttpClientFactory.casAuthCookieStorage.reset()
        delay(1_000L)
        return authClient.get(LOGIN_URL).bodyAsText()
    }

    private suspend fun continueAfterKick(body: String, username: String): PortalLoginResponse {
        val execution = PortalHtmlExtractor.casExecution(body)
            ?: error("踢出会话页面缺少 execution")
        val response = authClient.submitForm(
            url = LOGIN_URL,
            formParameters = Parameters.build {
                append("execution", execution)
                append("_eventId", "continue")
            },
        ) {
            parameter("username", username)
            header(HttpHeaders.Origin, AUTH_ORIGIN)
            header(HttpHeaders.Referrer, LOGIN_URL)
        }
        if (!response.status.isRedirect()) {
            throw IllegalStateException("踢出旧会话失败")
        }
        val castgc = PortalHtmlExtractor.castgcFromSetCookie(response.headers.getAll(HttpHeaders.SetCookie).orEmpty())
            ?: error("踢出旧会话后未返回 CASTGC")
        val jwxtClient = jwxtClientFactory(castgc)
        val oauthResponse = jwxtClient.get("/login/oauth2")
        if (!oauthResponse.ok) error("踢出会话后 OAuth2 桥接失败，状态码 ${oauthResponse.status}")
        if (!oauthResponse.url.startsWith("https://jwxt.jsu.edu.cn"))
            error("踢出会话后 OAuth2 桥接失败：最终页面不在教务系统域 (${oauthResponse.url})")
        if (oauthResponse.text.contains("统一身份认证", ignoreCase = true))
            error("踢出会话后 OAuth2 桥接失败：获取到统一认证页面")
        return PortalLoginResponse(
            castgc = castgc,
            kicked = true,
        )
    }

    private fun HttpStatusCode.isRedirect(): Boolean = value in 300..399

    private companion object {
        const val LOGIN_URL = "https://authserver.jsu.edu.cn/authserver/login"
        const val AUTH_ORIGIN = "https://authserver.jsu.edu.cn"
    }
}
