package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.Credentials
import com.shisan.campuspro.core.model.LoginMode
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class JwxtDirectSessionTest {
    @Test
    fun `direct login records JWXT_DIRECT mode even when not remembered`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = "<html>学生个人中心</html>",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val sessionFlagStore = FakeSessionFlagStore(hasSession = false)
        val session = JwxtDirectSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = FakeCredentialsProvider(),
            sessionFlagStore = sessionFlagStore,
        )

        assertEquals(
            LoginResult.Success,
            session.login("20260001", "secret", rememberCredentials = false),
        )
        assertEquals(LoginMode.JWXT_DIRECT, sessionFlagStore.getLoginMode())
    }

    @Test
    fun `autoLogin returns missing credentials when no saved account exists`() = runBlocking {
        val session = JwxtDirectSession(
            client = JwxtClient(client = HttpClient(MockEngine { error("network should not be called") })),
            credentialsProvider = FakeCredentialsProvider(),
            sessionFlagStore = FakeSessionFlagStore(hasSession = false),
        )

        assertEquals(AutoLoginResult.MissingCredentials, session.autoLogin())
    }

    @Test
    fun `logout clears loginMode from SessionFlagStore`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = "<html>学生个人中心</html>",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val sessionFlagStore = FakeSessionFlagStore(hasSession = false)
        val session = JwxtDirectSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = FakeCredentialsProvider(
                credentials = Credentials("20260001", "secret"),
            ),
            sessionFlagStore = sessionFlagStore,
        )

        // 先登录 JWXT_DIRECT 模式
        assertEquals(
            LoginResult.Success,
            session.login("20260001", "secret", rememberCredentials = false),
        )
        assertEquals(LoginMode.JWXT_DIRECT, sessionFlagStore.getLoginMode())

        // 登出后 loginMode 应该被清除
        session.logout()
        assertEquals(null, sessionFlagStore.getLoginMode())
    }

    @Test
    fun `autoLogin relogs in with saved credentials when session is missing`() = runBlocking {
        val credentialsProvider = FakeCredentialsProvider(
            credentials = Credentials("20260001", "secret"),
        )
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/jsxsd/xk/LoginToXk", request.url.encodedPath)
            respond(
                content = "<html>学生个人中心</html>",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val session = JwxtDirectSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = credentialsProvider,
            sessionFlagStore = FakeSessionFlagStore(hasSession = false),
        )

        assertEquals(AutoLoginResult.ReloggedIn, session.autoLogin())
    }

    @Test
    fun `autoLogin does not treat long auth page as valid session`() = runBlocking {
        val authPage = buildString {
            append("<html><body>统一身份认证 authserver")
            repeat(1200) { append("认证页面") }
            append("</body></html>")
        }
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/jsxsd/xskb/xskb_list.do", request.url.encodedPath)
            respond(
                content = authPage,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val sessionFlagStore = FakeSessionFlagStore(hasSession = true)
        val session = JwxtDirectSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = FakeCredentialsProvider(),
            sessionFlagStore = sessionFlagStore,
        )

        assertEquals(AutoLoginResult.MissingCredentials, session.autoLogin())
        assertFalse(sessionFlagStore.hasSession())
    }

    @Test
    fun `forced autoLogin ignores cached successful probe and relogs in`() = runBlocking {
        val credentialsProvider = FakeCredentialsProvider(
            credentials = Credentials("20260001", "secret"),
        )
        val methods = mutableListOf<HttpMethod>()
        val paths = mutableListOf<String>()
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount += 1
            methods += request.method
            paths += request.url.encodedPath
            when (requestCount) {
                1 -> respond(
                    content = """<html><table id="kbtable"></table></html>""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                )
                2 -> respond(
                    content = """<html><form id="login"></form></html>""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                )
                else -> respond(
                    content = "<html>学生个人中心</html>",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                )
            }
        }
        val session = JwxtDirectSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = credentialsProvider,
            sessionFlagStore = FakeSessionFlagStore(hasSession = true),
        )

        assertEquals(AutoLoginResult.SessionValid, session.autoLogin())
        assertEquals(AutoLoginResult.ReloggedIn, session.autoLogin(forceRefresh = true))

        assertEquals(listOf(HttpMethod.Get, HttpMethod.Get, HttpMethod.Post), methods)
        assertEquals(
            listOf(
                "/jsxsd/xskb/xskb_list.do",
                "/jsxsd/xskb/xskb_list.do",
                "/jsxsd/xk/LoginToXk",
            ),
            paths,
        )
    }

    @Test
    fun `concurrent forced autoLogin calls share one relogin`() = runBlocking {
        val credentialsProvider = FakeCredentialsProvider(
            credentials = Credentials("20260001", "secret"),
        )
        val loggedIn = AtomicBoolean(false)
        val loginPosts = AtomicInteger(0)
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Post && request.url.encodedPath == "/jsxsd/xk/LoginToXk") {
                loginPosts.incrementAndGet()
                loggedIn.set(true)
                respond(
                    content = "<html>学生个人中心</html>",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                )
            } else if (loggedIn.get()) {
                respond(
                    content = """<html><table id="kbtable"></table></html>""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                )
            } else {
                delay(50)
                respond(
                    content = """<html><form id="login"></form></html>""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                )
            }
        }
        val session = JwxtDirectSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = credentialsProvider,
            sessionFlagStore = FakeSessionFlagStore(hasSession = true),
        )

        val results = List(3) {
            async(Dispatchers.Default) { session.autoLogin(forceRefresh = true) }
        }.awaitAll()

        assertEquals(1, results.count { it == AutoLoginResult.ReloggedIn })
        assertEquals(2, results.count { it == AutoLoginResult.SessionValid })
        assertEquals(1, loginPosts.get())
    }

    @Test
    fun `autoLogin reports network timeout without clearing saved credentials`() = runBlocking {
        val credentialsProvider = FakeCredentialsProvider(
            credentials = Credentials("20260001", "secret"),
        )
        val engine = MockEngine {
            throw SocketTimeoutException("read timed out")
        }
        val session = JwxtDirectSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = credentialsProvider,
            sessionFlagStore = FakeSessionFlagStore(hasSession = false),
        )

        val result = session.autoLogin()

        assertEquals(
            AutoLoginResult.NetworkFailure(LoginFailureReason.NetworkTimeout, "网络连接超时，请稍后重试"),
            result,
        )
        assertFalse(credentialsProvider.credentialsCleared)
    }

    @Test
    fun `autoLogin reports captcha requirement as rejected credentials`() = runBlocking {
        val credentialsProvider = FakeCredentialsProvider(
            credentials = Credentials("20260001", "secret"),
        )
        val engine = MockEngine {
            respond(
                content = "<html>验证码错误</html>",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val session = JwxtDirectSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = credentialsProvider,
            sessionFlagStore = FakeSessionFlagStore(hasSession = false),
        )

        assertEquals(
            AutoLoginResult.CredentialsRejected(
                LoginFailureReason.CaptchaRequired,
                "需要验证码，请在电脑端登录后再试",
            ),
            session.autoLogin(),
        )
    }

    @Test
    fun `course page uses get request like rust jwxt client`() = runBlocking {
        val methods = mutableListOf<HttpMethod>()
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            methods += request.method
            paths += request.url.encodedPath
            respond(
                content = """<html><table id="kbtable"></table></html>""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val session = JwxtDirectSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = FakeCredentialsProvider(),
            sessionFlagStore = FakeSessionFlagStore(),
        )

        session.getCoursePage("2025-2026-2")

        assertEquals(listOf(HttpMethod.Get), methods)
        assertEquals(listOf("/jsxsd/xskb/xskb_list.do"), paths)
    }

    @Test
    fun logoutClearsSessionFlagAndSavedCredentials() = runBlocking {
        JwxtClient.sharedCookieStorage.addCookie(
            Url("https://authserver.jsu.edu.cn/authserver/login"),
            Cookie(name = "CASTGC", value = "TGT-secret", path = "/authserver"),
        )
        val credentialsProvider = FakeCredentialsProvider()
        val sessionFlagStore = FakeSessionFlagStore()
        val session = JwxtDirectSession(
            client = JwxtClient(),
            credentialsProvider = credentialsProvider,
            sessionFlagStore = sessionFlagStore,
        )

        session.logout()

        assertFalse(sessionFlagStore.hasSession())
        assertTrue(credentialsProvider.credentialsCleared)
        assertTrue(
            JwxtClient.sharedCookieStorage
                .get(Url("https://authserver.jsu.edu.cn/authserver/login"))
                .isEmpty(),
        )
    }

    @Test
    fun `successful login does not save credentials when remember is disabled`() = runBlocking {
        val credentialsProvider = FakeCredentialsProvider()
        val session = successfulDirectLoginSession(credentialsProvider)

        assertEquals(
            LoginResult.Success,
            session.login("20260001", "secret", rememberCredentials = false),
        )

        assertNull(credentialsProvider.savedCredentials)
        assertTrue(credentialsProvider.credentialsCleared)
    }

    @Test
    fun `successful login saves credentials when remember is enabled`() = runBlocking {
        val credentialsProvider = FakeCredentialsProvider()
        val session = successfulDirectLoginSession(credentialsProvider)

        assertEquals(
            LoginResult.Success,
            session.login("20260001", "secret", rememberCredentials = true),
        )

        assertEquals(
            Credentials("20260001", "secret", LoginMode.JWXT_DIRECT),
            credentialsProvider.savedCredentials,
        )
        assertFalse(credentialsProvider.credentialsCleared)
    }

    private fun successfulDirectLoginSession(credentialsProvider: FakeCredentialsProvider): JwxtDirectSession {
        val engine = MockEngine {
            respond(
                content = "<html>欢迎您 我的课表 成绩查询</html>",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        return JwxtDirectSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = credentialsProvider,
            sessionFlagStore = FakeSessionFlagStore(hasSession = false),
        )
    }
}
