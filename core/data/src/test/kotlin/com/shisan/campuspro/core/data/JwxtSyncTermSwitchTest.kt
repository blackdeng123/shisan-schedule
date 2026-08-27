package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.AcademicUpdateDiff
import com.shisan.campuspro.core.model.Credentials
import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import com.shisan.campuspro.core.model.LoginMode
import com.shisan.campuspro.core.model.NotificationSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import com.shisan.campuspro.core.network.CredentialsProvider
import com.shisan.campuspro.core.network.JwxtClient
import com.shisan.campuspro.core.network.JwxtDirectSession
import com.shisan.campuspro.core.network.PortalSession
import com.shisan.campuspro.core.network.SessionFlagStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val OLD_TERM = "2025-2026-2"
private const val NEW_TERM = "2026-2027-1"

/**
 * 学期切换回归测试：教务系统在开学前发布新学期课表时，
 * 同步必须以教务系统下拉框选中的学期为准，并为新学期单独建表，
 * 而不是沿用按日期推算的旧学期、把数据覆盖进上学期的课表。
 */
class JwxtSyncTermSwitchTest {

    @Test
    fun `syncAll uses jwxt selected term and keeps old schedule untouched`() = runBlocking {
        val scheduleRepository = InMemoryScheduleRepository()
        val oldSchedule = createEmptySchedule(OLD_TERM).copy(
            courses = listOf(course(id = "old-course", name = "旧学期课程")),
        )
        scheduleRepository.upsertSchedule(oldSchedule)

        val repository = buildSyncRepository(scheduleRepository) { request ->
            val term = request.url.parameters["xnxq01id"]
            when (request.url.encodedPath) {
                "/jsxsd/xskb/xskb_list.do" -> when {
                    term.isNullOrBlank() -> coursePageHtml(selectedTerm = NEW_TERM, courseName = "新学期课程")
                    term == NEW_TERM -> coursePageHtml(selectedTerm = NEW_TERM, courseName = "新学期课程")
                    term == OLD_TERM -> coursePageHtml(selectedTerm = OLD_TERM, courseName = "旧学期课程")
                    else -> coursePageHtml(selectedTerm = null, courseName = null)
                }
                "/jsxsd/kscj/cjcx_list" -> "<html><table id=\"dataList\"></table></html>"
                else -> "<html></html>"
            }
        }

        val result = repository.syncAll(autoSync = false)

        assertTrue("课表同步应成功：${result.schedule?.error}", result.schedule?.success == true)
        val schedules = scheduleRepository.observeSchedules().first()
        assertEquals(listOf(OLD_TERM, NEW_TERM), schedules.mapNotNull { it.termId }.sorted())

        val newSchedule = schedules.first { it.termId == NEW_TERM }
        assertEquals(listOf("新学期课程"), newSchedule.courses.map { it.name })

        // 新学期课表应被激活，旧学期课表原样保留
        assertEquals(NEW_TERM, scheduleRepository.observeActiveSchedule().first()?.termId)
        val untouchedOld = schedules.first { it.termId == OLD_TERM }
        assertEquals(listOf("旧学期课程"), untouchedOld.courses.map { it.name })
    }

    @Test
    fun `sync falls back to date-inferred term when jwxt page lacks selected option`() = runBlocking {
        val scheduleRepository = InMemoryScheduleRepository()
        val repository = buildSyncRepository(scheduleRepository) { request ->
            val term = request.url.parameters["xnxq01id"]
            when {
                request.url.encodedPath == "/jsxsd/xskb/xskb_list.do" && term.isNullOrBlank() ->
                    // 无下拉框的异常页面：探测应回退到日期推算
                    "<html><table id=\"kbtable\"></table></html>"
                request.url.encodedPath == "/jsxsd/xskb/xskb_list.do" ->
                    coursePageHtml(selectedTerm = term, courseName = "课程-$term")
                request.url.encodedPath == "/jsxsd/kscj/cjcx_list" ->
                    "<html><table id=\"dataList\"></table></html>"
                else -> "<html></html>"
            }
        }

        val result = repository.syncAll(autoSync = false)

        assertTrue("课表同步应成功：${result.schedule?.error}", result.schedule?.success == true)
        val active = scheduleRepository.observeActiveSchedule().first()
        assertNotNull(active)
        assertEquals(TermPolicy.currentTermId(), active?.termId)
    }

    private fun buildSyncRepository(
        scheduleRepository: ScheduleRepository,
        pageProvider: (io.ktor.client.request.HttpRequestData) -> String,
    ): JwxtSyncRepository {
        val engine = MockEngine { request ->
            respond(
                content = pageProvider(request),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val client = JwxtClient(client = HttpClient(engine))
        val credentialsProvider = FakeCredentialsProvider()
        val sessionFlagStore = FakeSessionFlagStore()
        return JwxtSyncRepository(
            directSession = JwxtDirectSession(client, credentialsProvider, sessionFlagStore),
            portalSession = PortalSession(client, credentialsProvider, sessionFlagStore),
            credentialsProvider = credentialsProvider,
            sessionFlagStore = sessionFlagStore,
            scheduleRepository = scheduleRepository,
            gradesRepository = InMemoryGradesRepository(),
            examsRepository = InMemoryExamsRepository(),
            notificationPreferenceStore = FakeNotificationPreferenceStore(),
            academicUpdateNotifier = NoopUpdateNotifier(),
            onScheduleUpdated = {},
        )
    }

    private fun coursePageHtml(selectedTerm: String?, courseName: String?): String = buildString {
        append("<html><body>")
        append("<select name=\"xnxq01id\">")
        listOf(OLD_TERM, NEW_TERM).forEach { term ->
            val selected = if (term == selectedTerm) " selected" else ""
            append("<option value=\"$term\"$selected>$term</option>")
        }
        append("</select>")
        if (courseName != null) {
            append(
                """
                <table id="kbtable">
                  <tr><th>时间</th><th>星期一</th></tr>
                  <tr>
                    <td>第一大节</td>
                    <td>
                      <div id="A-1-1" class="kbcontent1">$courseName<br><font title="周次(节次)">1-3(周)</font><br><font title="教室">一教101</font></div>
                      <div id="A-1-2" class="kbcontent">$courseName<br><font title="老师">张老师</font></div>
                    </td>
                  </tr>
                </table>
                """.trimIndent(),
            )
        } else {
            append("<table id=\"kbtable\"></table>")
        }
        append("</body></html>")
    }

    private fun course(id: String, name: String) = Course(
        id = id,
        name = name,
        location = "一教101",
        teacher = "张老师",
        day = 1,
        startSlot = 1,
        endSlot = 2,
        color = "#72A5F2",
        weeks = listOf(1, 2, 3),
        source = CourseSource.JWXT,
        jwxtKey = id,
    )
}

private class FakeCredentialsProvider : CredentialsProvider {
    override suspend fun loadCredentials(): Credentials? =
        Credentials("20260001", "secret", loginMode = LoginMode.JWXT_DIRECT)

    override suspend fun saveCredentials(credentials: Credentials) = Unit

    override suspend fun clearCredentials() = Unit
}

private class FakeSessionFlagStore : SessionFlagStore {
    private var hasSession = true
    private var loginMode: LoginMode? = LoginMode.JWXT_DIRECT

    override suspend fun hasSession(): Boolean = hasSession

    override suspend fun setHasSession(value: Boolean) {
        hasSession = value
    }

    override suspend fun getLoginMode(): LoginMode? = loginMode

    override suspend fun setLoginMode(mode: LoginMode?) {
        loginMode = mode
    }
}

private class FakeNotificationPreferenceStore : NotificationPreferenceStore {
    private val settings = MutableStateFlow(NotificationSettings())

    override val notificationSettings: Flow<NotificationSettings> = settings

    override suspend fun updateNotificationSettings(transform: (NotificationSettings) -> NotificationSettings) {
        settings.update(transform)
    }
}

private class NoopUpdateNotifier : AcademicUpdateNotifier {
    override fun publishGradeUpdates(diff: AcademicUpdateDiff) = Unit

    override fun publishExamUpdates(diff: AcademicUpdateDiff) = Unit

    override fun canPostNotifications(): Boolean = false
}
