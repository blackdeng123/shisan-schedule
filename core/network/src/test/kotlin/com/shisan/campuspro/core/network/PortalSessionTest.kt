package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.Credentials
import com.shisan.campuspro.core.model.LoginMode
import com.shisan.campuspro.core.model.PortalWebSessionResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalSessionTest {
    @Test
    fun `castgc rejects missing and direct credentials`() = runBlocking {
        val missingSession = testSession(credentials = null)
        val directSession = testSession(
            credentials = Credentials("20260001", "secret", LoginMode.JWXT_DIRECT),
        )

        assertEquals(PortalWebSessionResult.PortalLoginRequired, missingSession.castgc())
        assertEquals(PortalWebSessionResult.PortalLoginRequired, directSession.castgc())
    }

    @Test
    fun `castgc returns existing portal CASTGC`() = runBlocking {
        JwxtClient.sharedCookieStorage.reset()
        JwxtClient.sharedCookieStorage.addCookie(
            requestUrl = Url("https://authserver.jsu.edu.cn/authserver/login"),
            cookie = Cookie(
                name = "CASTGC",
                value = "TGT-secret-value",
                domain = "authserver.jsu.edu.cn",
                path = "/authserver",
                secure = true,
                httpOnly = true,
            ),
        )
        val session = testSession(
            credentials = Credentials("20260001", "secret", LoginMode.PORTAL),
        )

        val result = session.castgc()

        assertTrue(result is PortalWebSessionResult.Ready)
        assertEquals("TGT-secret-value", (result as PortalWebSessionResult.Ready).castgc)
        JwxtClient.sharedCookieStorage.reset()
    }

    @Test
    fun `force refresh portal session ignores existing CASTGC and logs in again`() = runBlocking {
        JwxtClient.sharedCookieStorage.reset()
        JwxtClient.sharedCookieStorage.addCookie(
            requestUrl = Url("https://authserver.jsu.edu.cn/authserver/login"),
            cookie = Cookie(
                name = "CASTGC",
                value = "TGT-expired",
                domain = "authserver.jsu.edu.cn",
                path = "/authserver",
                secure = true,
                httpOnly = true,
            ),
        )
        var portalLoginCount = 0
        val engine = MockEngine {
            respond(
                content = """<html><table id="kbtable"></table></html>""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val session = PortalSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = FakeCredentialsProvider(
                Credentials("20260001", "secret", LoginMode.PORTAL),
            ),
            sessionFlagStore = FakeSessionFlagStore(hasSession = true),
            portalLogin = { _, _ ->
                portalLoginCount++
                JwxtClient.sharedCookieStorage.addCookie(
                    Url("https://authserver.jsu.edu.cn/authserver/login"),
                    Cookie(
                        name = "CASTGC",
                        value = "TGT-refreshed",
                        domain = "authserver.jsu.edu.cn",
                        path = "/authserver",
                        secure = true,
                        httpOnly = true,
                    ),
                )
                Result.success(PortalLoginResponse("TGT-refreshed", kicked = false))
            },
        )

        val result = session.castgc(forceRefresh = true)

        assertEquals(1, portalLoginCount)
        assertTrue(result is PortalWebSessionResult.Ready)
        assertEquals("TGT-refreshed", (result as PortalWebSessionResult.Ready).castgc)
        JwxtClient.sharedCookieStorage.reset()
    }

    @Test
    fun `castgc rebuilds a missing CASTGC with saved portal credentials`() = runBlocking {
        JwxtClient.sharedCookieStorage.reset()
        var portalLoginCount = 0
        val engine = MockEngine {
            respond(
                content = """<html><table id="kbtable"></table></html>""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val session = PortalSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = FakeCredentialsProvider(
                Credentials("20260001", "secret", LoginMode.PORTAL),
            ),
            sessionFlagStore = FakeSessionFlagStore(hasSession = false),
            portalLogin = { _, _ ->
                portalLoginCount += 1
                JwxtClient.sharedCookieStorage.addCookie(
                    requestUrl = Url("https://authserver.jsu.edu.cn/authserver/login"),
                    cookie = Cookie(
                        name = "CASTGC",
                        value = "TGT-refreshed",
                        domain = "authserver.jsu.edu.cn",
                        path = "/authserver",
                        secure = true,
                        httpOnly = true,
                    ),
                )
                Result.success(PortalLoginResponse("TGT-refreshed", kicked = false))
            },
        )

        val result = session.castgc()

        assertEquals(1, portalLoginCount)
        assertTrue(result is PortalWebSessionResult.Ready)
        assertEquals("TGT-refreshed", (result as PortalWebSessionResult.Ready).castgc)
        JwxtClient.sharedCookieStorage.reset()
    }

    @Test
    fun `logout waits for portal session preparation and leaves no revived session`() = runBlocking {
        JwxtClient.sharedCookieStorage.reset()
        val portalLoginStarted = CompletableDeferred<Unit>()
        val allowPortalLogin = CompletableDeferred<Unit>()
        val engine = MockEngine {
            respond(
                content = """<html><table id="kbtable"></table></html>""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val sessionFlagStore = FakeSessionFlagStore(hasSession = false)
        val session = PortalSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = FakeCredentialsProvider(
                Credentials("20260001", "secret", LoginMode.PORTAL),
            ),
            sessionFlagStore = sessionFlagStore,
            portalLogin = { _, _ ->
                portalLoginStarted.complete(Unit)
                allowPortalLogin.await()
                JwxtClient.sharedCookieStorage.addCookie(
                    Url("https://authserver.jsu.edu.cn/authserver/login"),
                    Cookie(name = "CASTGC", value = "TGT-late", path = "/authserver"),
                )
                Result.success(PortalLoginResponse("TGT-late", kicked = false))
            },
        )

        val preparation = async { session.castgc() }
        portalLoginStarted.await()
        val logout = async { session.logout() }

        assertTrue(!logout.isCompleted)
        allowPortalLogin.complete(Unit)
        preparation.await()
        logout.await()

        assertEquals(null, sessionFlagStore.getLoginMode())
        assertTrue(
            JwxtClient.sharedCookieStorage
                .get(Url("https://authserver.jsu.edu.cn/authserver/login"))
                .isEmpty(),
        )
    }

    @Test
    fun `autoLogin SessionValid when flags say portal session and no credentials`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """<html><table id="kbtable"></table></html>""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val sessionFlagStore = FakeSessionFlagStore(
            hasSession = true,
            loginMode = LoginMode.PORTAL,
        )
        val session = PortalSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = FakeCredentialsProvider(), // no saved credentials
            sessionFlagStore = sessionFlagStore,
        )

        assertEquals(AutoLoginResult.SessionValid, session.autoLogin())
        assertEquals(LoginMode.PORTAL, sessionFlagStore.getLoginMode())
    }

    @Test
    fun `autoLogin SessionValid when saved portal credentials exist`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """<html><table id="kbtable"></table></html>""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val session = PortalSession(
            client = JwxtClient(client = HttpClient(engine)),
            credentialsProvider = FakeCredentialsProvider(
                credentials = Credentials("20260001", "secret", LoginMode.PORTAL),
            ),
            sessionFlagStore = FakeSessionFlagStore(hasSession = true),
        )

        assertEquals(AutoLoginResult.SessionValid, session.autoLogin())
    }

    private fun testSession(credentials: Credentials?): PortalSession =
        PortalSession(
            client = JwxtClient(client = HttpClient(MockEngine { error("network should not be called") })),
            credentialsProvider = FakeCredentialsProvider(credentials),
            sessionFlagStore = FakeSessionFlagStore(hasSession = false),
        )
}
