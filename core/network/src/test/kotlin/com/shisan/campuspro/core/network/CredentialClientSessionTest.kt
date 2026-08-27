package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.CredentialEmailDraft
import com.shisan.campuspro.core.model.CredentialFailure
import com.shisan.campuspro.core.model.CredentialFailureKind
import com.shisan.campuspro.core.model.PortalWebSessionResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRedirect
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialClientSessionTest {
    @Test
    fun `GET 收到 401 后只重建一次会话`() = runTest {
        var authenticationCount = 0
        var dashboardCount = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/authserver/oauth2.0/authorize" -> respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(
                        HttpHeaders.Location,
                        "https://kxdzpz.jsu.edu.cn/?code=code-${authenticationCount + 1}",
                    ),
                )
                "/" -> respond("")
                "/cas/clientJsdx/validateLogin" -> {
                    authenticationCount++
                    respondJson("""{"success":true,"result":{"token":"token-$authenticationCount"}}""")
                }
                "/student/dashboardZufe/index" -> {
                    dashboardCount++
                    if (dashboardCount == 1) {
                        respond("", HttpStatusCode.Unauthorized)
                    } else {
                        respondJson(
                            """{"success":true,"result":{"service_list":{"records":[]}}}""",
                        )
                    }
                }
                else -> error("未处理请求：${request.url}")
            }
        }
        val client = CredentialClient(
            portalSessionProvider = { PortalWebSessionResult.Ready("portal-cookie") },
            client = testClient(engine),
        )

        client.services()

        assertEquals(2, authenticationCount)
        assertEquals(2, dashboardCount)
    }

    @Test
    fun `POST 收到 401 后只重建一次会话`() = runTest {
        var authenticationCount = 0
        var postCount = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/authserver/oauth2.0/authorize" -> respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(
                        HttpHeaders.Location,
                        "https://kxdzpz.jsu.edu.cn/?code=code-${authenticationCount + 1}",
                    ),
                )
                "/" -> respond("")
                "/cas/clientJsdx/validateLogin" -> {
                    authenticationCount++
                    respondJson("""{"success":true,"result":{"token":"token-$authenticationCount"}}""")
                }
                "/student/caUserEmail/add" -> {
                    postCount++
                    if (postCount == 1) {
                        respond("", HttpStatusCode.Unauthorized)
                    } else {
                        respondJson("""{"success":true,"result":{}}""")
                    }
                }
                else -> error("未处理请求：${request.url}")
            }
        }
        val client = CredentialClient(
            portalSessionProvider = { PortalWebSessionResult.Ready("portal-cookie") },
            client = testClient(engine),
        )

        client.addEmail(
            CredentialEmailDraft("student@example.com", "", "电子凭证", "请查收"),
        )

        assertEquals(2, authenticationCount)
        assertEquals(2, postCount)
    }

    @Test
    fun `OAuth 跳回登录页后强制刷新门户会话一次`() = runTest {
        val forceRefreshCalls = mutableListOf<Boolean>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/authserver/oauth2.0/authorize" -> {
                    val cookie = request.headers[HttpHeaders.Cookie].orEmpty()
                    val location = if (cookie.contains("expired-cookie")) {
                        "https://authserver.jsu.edu.cn/authserver/login"
                    } else {
                        "https://kxdzpz.jsu.edu.cn/?code=refreshed-code"
                    }
                    respond(
                        "",
                        HttpStatusCode.Found,
                        headersOf(HttpHeaders.Location, location),
                    )
                }
                "/authserver/login", "/" -> respond("")
                "/cas/clientJsdx/validateLogin" -> respondJson(
                    """{"success":true,"result":{"token":"token"}}""",
                )
                "/student/dashboardZufe/index" -> respondJson(
                    """{"success":true,"result":{"service_list":{"records":[]}}}""",
                )
                else -> error("未处理请求：${request.url}")
            }
        }
        val client = CredentialClient(
            portalSessionProvider = { forceRefresh ->
                forceRefreshCalls += forceRefresh
                PortalWebSessionResult.Ready(
                    if (forceRefresh) "refreshed-cookie" else "expired-cookie",
                )
            },
            client = testClient(engine),
        )

        client.services()

        assertEquals(listOf(false, true), forceRefreshCalls)
    }

    @Test
    fun `门户会话强刷后仍跳登录页则要求重新门户登录`() = runTest {
        var sessionPreparationCount = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/authserver/oauth2.0/authorize" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.Location,
                        "https://authserver.jsu.edu.cn/authserver/login",
                    ),
                )
                "/authserver/login" -> respond("")
                else -> error("未处理请求：${request.url}")
            }
        }
        val client = CredentialClient(
            portalSessionProvider = {
                sessionPreparationCount++
                PortalWebSessionResult.Ready("expired-cookie")
            },
            client = testClient(engine),
        )

        val error = runCatching { client.services() }.exceptionOrNull()

        assertEquals(2, sessionPreparationCount)
        assertEquals(
            CredentialFailureKind.PortalLoginRequired,
            (error as CredentialFailure).kind,
        )
    }

    @Test
    fun `门户会话无法强制重建时要求重新门户登录`() = runTest {
        val forceRefreshCalls = mutableListOf<Boolean>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/authserver/oauth2.0/authorize" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.Location,
                        "https://authserver.jsu.edu.cn/authserver/login",
                    ),
                )
                "/authserver/login" -> respond("")
                else -> error("未处理请求：${request.url}")
            }
        }
        val client = CredentialClient(
            portalSessionProvider = { forceRefresh ->
                forceRefreshCalls += forceRefresh
                if (forceRefresh) {
                    PortalWebSessionResult.Failure("门户认证失败")
                } else {
                    PortalWebSessionResult.Ready("expired-cookie")
                }
            },
            client = testClient(engine),
        )

        val error = runCatching { client.services() }.exceptionOrNull()

        assertEquals(listOf(false, true), forceRefreshCalls)
        assertEquals(
            CredentialFailureKind.PortalLoginRequired,
            (error as CredentialFailure).kind,
        )
        assertEquals("门户会话已失效，请重新使用门户登录", error.message)
    }

    @Test
    fun `OAuth 明确拒绝时才映射为未授予权限`() = runTest {
        val client = CredentialClient(
            portalSessionProvider = { PortalWebSessionResult.Ready("portal-cookie") },
            client = oauthCallbackClient("?error=access_denied"),
        )

        val error = runCatching { client.services() }.exceptionOrNull()

        assertEquals(CredentialFailureKind.Authentication, (error as CredentialFailure).kind)
        assertEquals("学校未授予可信电子凭证访问权限", error.message)
    }

    @Test
    fun `OAuth 未知回调不会误报未授予权限`() = runTest {
        val client = CredentialClient(
            portalSessionProvider = { PortalWebSessionResult.Ready("portal-cookie") },
            client = oauthCallbackClient("?unexpected=true"),
        )

        val error = runCatching { client.services() }.exceptionOrNull()

        assertEquals("学校统一认证回调格式已变化", error?.message)
    }

    @Test
    fun `令牌交换的 state 参数只编码一次`() = runTest {
        var receivedState: String? = null
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/authserver/oauth2.0/authorize" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.Location,
                        "https://kxdzpz.jsu.edu.cn/?code=oauth-code",
                    ),
                )
                "/" -> respond("")
                "/cas/clientJsdx/validateLogin" -> {
                    receivedState = request.url.parameters["state"]
                    respondJson("""{"success":true,"result":{"token":"token"}}""")
                }
                "/student/dashboardZufe/index" -> respondJson(
                    """{"success":true,"result":{"service_list":{"records":[]}}}""",
                )
                else -> error("未处理请求：${request.url}")
            }
        }
        val client = CredentialClient(
            portalSessionProvider = { PortalWebSessionResult.Ready("portal-cookie") },
            client = testClient(engine),
        )

        client.services()

        assertEquals(CredentialEndpoints.ServiceOrigin, receivedState)
    }

    private fun testClient(engine: MockEngine) = HttpClient(engine) {
        install(HttpRedirect)
        followRedirects = true
    }

    private fun oauthCallbackClient(query: String): HttpClient {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/authserver/oauth2.0/authorize" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.Location,
                        CredentialEndpoints.ServiceOrigin + "/" + query,
                    ),
                )
                "/" -> respond("")
                else -> error("未处理请求：${request.url}")
            }
        }
        return testClient(engine)
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(body: String) =
        respond(
            content = body,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
}
