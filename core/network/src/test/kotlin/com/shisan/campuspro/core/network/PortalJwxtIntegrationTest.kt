package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.Credentials
import com.shisan.campuspro.core.model.LoginMode
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PortalJwxtIntegrationTest {
    @Test
    fun `portal and jwxt direct schedules match when env integration is enabled`() = runBlocking {
        assumeTrue("真实门户集成测试默认跳过", integrationEnabled())
        val env = loadProjectEnv()
        val username = env.firstExisting("CAMPUS_USERNAME", "JWXT_USERNAME", "PORTAL_USERNAME", "USERNAME")
        val password = env.firstExisting("CAMPUS_PASSWORD", "JWXT_PASSWORD", "PORTAL_PASSWORD", "PASSWORD")
        val termId = env["CAMPUS_TERM_ID"] ?: "2025-2026-2"
        assumeTrue("缺少 .env 凭据，跳过真实门户集成测试", !username.isNullOrBlank() && !password.isNullOrBlank())
        val safeUsername = checkNotNull(username)
        val safePassword = checkNotNull(password)

        val directSession = testDirectSession()
        val portalSession = testPortalSession()

        assertLoginSuccess(directSession.login(safeUsername, safePassword, rememberCredentials = true), "JWXT_DIRECT")
        assertLoginSuccess(portalSession.login(safeUsername, safePassword, rememberCredentials = true), "PORTAL")

        val directScheduleHtml = directSession.getCoursePage(termId)
        val portalScheduleHtml = portalSession.getCoursePage(termId)
        assertValidJwxtPage("JWXT_DIRECT 课表", directScheduleHtml)
        assertValidJwxtPage("PORTAL 课表", portalScheduleHtml)
        assertTrue("JWXT_DIRECT 课表页缺少 kbtable", directScheduleHtml.contains("kbtable"))
        assertTrue("PORTAL 课表页缺少 kbtable，门户 OAuth 后没有进入有效教务会话", portalScheduleHtml.contains("kbtable"))

        val directSchedule = ScheduleParser.parse(directScheduleHtml).courses
        val portalSchedule = ScheduleParser.parse(portalScheduleHtml).courses
        val diff = ScheduleComparison.diff(portalSchedule, directSchedule)

        assertTrue(diff.redactedSummary(), diff.isEmpty)
    }

    @Test
    fun `portal auto login can sync schedule and grades when env integration is enabled`() = runBlocking {
        assumeTrue("真实门户集成测试默认跳过", integrationEnabled())
        val env = loadProjectEnv()
        val username = env.firstExisting("CAMPUS_USERNAME", "JWXT_USERNAME", "PORTAL_USERNAME", "USERNAME")
        val password = env.firstExisting("CAMPUS_PASSWORD", "JWXT_PASSWORD", "PORTAL_PASSWORD", "PASSWORD")
        val termId = env["CAMPUS_TERM_ID"] ?: "2025-2026-2"
        assumeTrue("缺少 .env 凭据，跳过真实门户集成测试", !username.isNullOrBlank() && !password.isNullOrBlank())
        val safeUsername = checkNotNull(username)
        val safePassword = checkNotNull(password)

        val session = testPortalSession()
        assertLoginSuccess(session.login(safeUsername, safePassword, rememberCredentials = true), "PORTAL")

        val autoLogin = session.autoLogin()
        assertTrue(
            "PORTAL autoLogin 未保持可同步会话：$autoLogin",
            autoLogin == AutoLoginResult.SessionValid || autoLogin == AutoLoginResult.ReloggedIn,
        )
        val scheduleHtml = session.getCoursePage(termId)
        val gradesHtml = session.getGradesPage()
        val examsHtml = session.getExamPage(termId)
        assertValidJwxtPage("PORTAL 同步课程", scheduleHtml)
        assertValidJwxtPage("PORTAL 同步成绩", gradesHtml)
        assertValidJwxtPage("PORTAL 同步考试", examsHtml)
        assertTrue("PORTAL 同步课程返回的不是有效课表页", scheduleHtml.contains("kbtable"))
        assertTrue("PORTAL 同步成绩返回的不是有效成绩页", gradesHtml.contains("dataList"))
        assertTrue("PORTAL 同步考试返回的不是有效考试页", examsHtml.contains("dataList"))
    }

    private fun integrationEnabled(): Boolean =
        System.getProperty("campus.integration") == "true" ||
            System.getenv("CAMPUS_INTEGRATION") == "true"

    private fun loadProjectEnv(): Map<String, String> {
        val envFile = generateSequence(File(System.getProperty("user.dir")).canonicalFile) { it.parentFile }
            .map { File(it, ".env") }
            .firstOrNull { it.exists() }
            ?: return emptyMap()
        return envFile.readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && "=" in it }
            .map { line ->
                val key = line.substringBefore("=").trim()
                val value = line.substringAfter("=").trim().trim('"', '\'')
                key to value
            }
            .toMap()
    }

    private fun Map<String, String>.firstExisting(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> this[key]?.takeIf { it.isNotBlank() } }

    private fun testDirectSession(): JwxtDirectSession =
        JwxtDirectSession(
            client = JwxtClient(),
            credentialsProvider = fakeCredentialsProvider(),
            sessionFlagStore = fakeSessionFlagStore(),
        )

    private fun testPortalSession(): PortalSession =
        PortalSession(
            client = JwxtClient(),
            credentialsProvider = fakeCredentialsProvider(),
            sessionFlagStore = fakeSessionFlagStore(),
        )

    private fun fakeCredentialsProvider(): CredentialsProvider = object : CredentialsProvider {
        private var credentials: Credentials? = null
        override suspend fun loadCredentials(): Credentials? = credentials
        override suspend fun saveCredentials(credentials: Credentials) {
            this.credentials = credentials
        }
        override suspend fun clearCredentials() {
            credentials = null
        }
    }

    private fun fakeSessionFlagStore(): SessionFlagStore = object : SessionFlagStore {
        private var hasSession = false
        private var loginMode: LoginMode? = null
        override suspend fun hasSession(): Boolean = hasSession
        override suspend fun setHasSession(value: Boolean) {
            hasSession = value
        }
        override suspend fun getLoginMode(): LoginMode? = loginMode
        override suspend fun setLoginMode(mode: LoginMode?) {
            loginMode = mode
        }
    }

    private fun assertLoginSuccess(result: LoginResult, mode: String) {
        assertTrue(
            "$mode 登录失败：${(result as? LoginResult.Failure)?.message ?: "unknown"}",
            result is LoginResult.Success,
        )
    }

    private fun assertValidJwxtPage(label: String, html: String) {
        val lower = html.lowercase()
        assertFalse("$label 返回了统一认证/authserver 页面", html.contains("统一身份认证") || lower.contains("authserver"))
        assertFalse("$label 返回了教务系统令牌超时页面", html.contains("令牌") && html.contains("超时"))
    }
}
