package com.shisan.campuspro.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 门户 CAS 登录流程回归测试。
 *
 * 重点覆盖：退出登录后重新门户登录时，残留的 authserver 会话 Cookie
 * 不能干扰登录页获取（否则登录页会缺少 execution 表单字段）。
 */
class PortalCasSessionLoginTest {

    @After
    fun tearDown() {
        HttpClientFactory.casAuthCookieStorage.reset()
        JwxtClient.sharedCookieStorage.reset()
    }

    @Test
    fun `重新门户登录时清除上一次残留的 authserver 会话 Cookie`() = runBlocking {
        // 模拟上一次登录残留在 CAS 独立 Cookie 存储中的会话
        HttpClientFactory.casAuthCookieStorage.addCookie(
            requestUrl = Url("https://authserver.jsu.edu.cn/authserver/login"),
            cookie = Cookie(
                name = "CASTGC",
                value = "TGT-stale",
                domain = "authserver.jsu.edu.cn",
                path = "/authserver",
                secure = true,
                httpOnly = true,
            ),
        )
        var loginPageCookieHeader: String? = null
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> {
                    loginPageCookieHeader = request.headers[HttpHeaders.Cookie]
                    respond(
                        content = loginPageHtml,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                    )
                }
                HttpMethod.Post -> respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(
                        HttpHeaders.SetCookie,
                        "CASTGC=TGT-fresh; Path=/authserver; Secure; HttpOnly",
                    ),
                )
                else -> error("未处理请求：${request.method} ${request.url}")
            }
        }
        val session = PortalCasSession(
            authClient = casTestClient(engine),
            jwxtClientFactory = { _ -> jwxtBridgeClient() },
        )

        val result = session.login("20260001", "secret")

        assertTrue("残留 Cookie 不应导致登录失败：${result.exceptionOrNull()}", result.isSuccess)
        // 登录前必须重置 Cookie 存储，首次抓取登录页不允许携带残留会话
        assertNull("登录页请求不应携带残留的 authserver Cookie", loginPageCookieHeader)
    }

    @Test
    fun `登录页首次返回缺少 execution 时重置 Cookie 后重试成功`() = runBlocking {
        var getLoginCount = 0
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> {
                    getLoginCount += 1
                    val body = if (getLoginCount == 1) pageWithoutExecution else loginPageHtml
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                    )
                }
                HttpMethod.Post -> respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(
                        HttpHeaders.SetCookie,
                        "CASTGC=TGT-fresh; Path=/authserver; Secure; HttpOnly",
                    ),
                )
                else -> error("未处理请求：${request.method} ${request.url}")
            }
        }
        val session = PortalCasSession(
            authClient = casTestClient(engine),
            jwxtClientFactory = { _ -> jwxtBridgeClient() },
        )

        val result = session.login("20260001", "secret")

        assertTrue("重试后应登录成功：${result.exceptionOrNull()}", result.isSuccess)
        assertTrue("应重试抓取登录页", getLoginCount >= 2)
    }

    private fun casTestClient(engine: MockEngine) = HttpClient(engine) {
        install(HttpCookies) {
            storage = HttpClientFactory.casAuthCookieStorage
        }
    }

    /** OAuth2 桥接用的假教务客户端：返回教务系统域内的成功页面。 */
    private fun jwxtBridgeClient(): JwxtClient {
        val engine = MockEngine {
            respond(
                content = """<html><table id="kbtable"></table></html>""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        return JwxtClient(client = HttpClient(engine))
    }

    private companion object {
        val loginPageHtml = """
            <form id="casLoginForm">
                <input type="hidden" id="lt" value="LT-1"/>
                <input type="hidden" name="execution" value="e1s1"/>
                <input type="hidden" name="_eventId" value="submit"/>
            </form>
        """.trimIndent()

        const val pageWithoutExecution = "<html><body>会话已失效</body></html>"
    }
}
