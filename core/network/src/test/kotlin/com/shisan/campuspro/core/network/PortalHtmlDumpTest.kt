package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.Credentials
import com.shisan.campuspro.core.model.LoginMode
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PortalHtmlDumpTest {
    @Test
    fun `dump portal bridged jwxt html when env integration is enabled`() = runBlocking {
        assumeTrue("真实门户集成测试默认跳过", integrationEnabled())
        val env = loadProjectEnv()
        val username = env.firstExisting("CAMPUS_USERNAME", "JWXT_USERNAME", "PORTAL_USERNAME", "USERNAME")
        val password = env.firstExisting("CAMPUS_PASSWORD", "JWXT_PASSWORD", "PORTAL_PASSWORD", "PASSWORD")
        val termId = env["CAMPUS_TERM_ID"] ?: "2025-2026-2"
        assumeTrue("缺少 .env 凭据，跳过真实门户 HTML 导出测试", !username.isNullOrBlank() && !password.isNullOrBlank())

        val session = testSession()
        val login = session.login(checkNotNull(username), checkNotNull(password), rememberCredentials = true)
        assertTrue(
            "PORTAL 登录失败：${(login as? LoginResult.Failure)?.message ?: "unknown"}",
            login is LoginResult.Success,
        )

        val scheduleHtml = session.getCoursePage(termId)
        val gradesHtml = session.getGradesPage()
        val examsHtml = session.getExamPage(termId)

        assertTrue("PORTAL 课表页缺少 kbtable，未确认进入教务会话", scheduleHtml.contains("kbtable"))
        assertTrue("PORTAL 成绩页缺少 dataList，未确认进入教务会话", gradesHtml.contains("dataList"))
        assertTrue("PORTAL 考试页缺少 dataList，未确认进入教务会话", examsHtml.contains("dataList"))

        val schedule = JwxtHtmlParser.parseSchedule(scheduleHtml)
        val grades = JwxtHtmlParser.parseGrades(gradesHtml)
        val exams = JwxtHtmlParser.parseExams(examsHtml)
        assertTrue("PORTAL 课表页已进入教务，但解析结果为空", schedule.courses.isNotEmpty())
        assertTrue("PORTAL 成绩页已进入教务，但解析结果为空", grades.isNotEmpty())
        assertTrue("PORTAL 考试页已进入教务，但解析结果为空", exams.isNotEmpty())

        val outputDir = File("build/portal-html").apply { mkdirs() }
        File(outputDir, "portal-schedule.html").writeText(scheduleHtml)
        File(outputDir, "portal-grades.html").writeText(gradesHtml)
        File(outputDir, "portal-exams.html").writeText(examsHtml)
    }

    @Test
    fun `portal bridged jwxt pages parse when fetched concurrently`() = runBlocking {
        assumeTrue("真实门户集成测试默认跳过", integrationEnabled())
        val env = loadProjectEnv()
        val username = env.firstExisting("CAMPUS_USERNAME", "JWXT_USERNAME", "PORTAL_USERNAME", "USERNAME")
        val password = env.firstExisting("CAMPUS_PASSWORD", "JWXT_PASSWORD", "PORTAL_PASSWORD", "PASSWORD")
        val termId = env["CAMPUS_TERM_ID"] ?: "2025-2026-2"
        assumeTrue("缺少 .env 凭据，跳过真实门户并发同步测试", !username.isNullOrBlank() && !password.isNullOrBlank())

        val session = testSession()
        val login = session.login(checkNotNull(username), checkNotNull(password), rememberCredentials = true)
        assertTrue(
            "PORTAL 登录失败：${(login as? LoginResult.Failure)?.message ?: "unknown"}",
            login is LoginResult.Success,
        )

        val (scheduleHtml, gradesHtml, examsHtml) = coroutineScope {
            val scheduleDeferred = async { session.getCoursePage(termId) }
            val gradesDeferred = async { session.getGradesPage() }
            val examsDeferred = async { session.getExamPage(termId) }
            Triple(scheduleDeferred.await(), gradesDeferred.await(), examsDeferred.await())
        }

        assertTrue("PORTAL 并发课表页缺少 kbtable", scheduleHtml.contains("kbtable"))
        assertTrue("PORTAL 并发成绩页缺少 dataList", gradesHtml.contains("dataList"))
        assertTrue("PORTAL 并发考试页缺少 dataList", examsHtml.contains("dataList"))
        assertTrue("PORTAL 并发课表解析结果为空", JwxtHtmlParser.parseSchedule(scheduleHtml).courses.isNotEmpty())
        assertTrue("PORTAL 并发成绩解析结果为空", JwxtHtmlParser.parseGrades(gradesHtml).isNotEmpty())
        assertTrue("PORTAL 并发考试解析结果为空", JwxtHtmlParser.parseExams(examsHtml).isNotEmpty())
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

    private fun testSession(): PortalSession =
        PortalSession(
            client = JwxtClient(),
            credentialsProvider = object : CredentialsProvider {
                private var credentials: Credentials? = null
                override suspend fun loadCredentials(): Credentials? = credentials
                override suspend fun saveCredentials(credentials: Credentials) {
                    this.credentials = credentials
                }
                override suspend fun clearCredentials() {
                    credentials = null
                }
            },
            sessionFlagStore = object : SessionFlagStore {
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
            },
        )
}
