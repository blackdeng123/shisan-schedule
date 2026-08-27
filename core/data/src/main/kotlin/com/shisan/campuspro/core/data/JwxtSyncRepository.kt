package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.FullSyncResult
import com.shisan.campuspro.core.model.LoginMode
import com.shisan.campuspro.core.model.Schedule
import com.shisan.campuspro.core.model.SyncFailureReason
import com.shisan.campuspro.core.model.SyncResult
import com.shisan.campuspro.core.model.SyncType
import com.shisan.campuspro.core.model.userMessage
import com.shisan.campuspro.core.network.AutoLoginResult
import com.shisan.campuspro.core.network.CredentialsProvider
import com.shisan.campuspro.core.network.JwxtDirectSession
import com.shisan.campuspro.core.network.JwxtHtmlParser
import com.shisan.campuspro.core.network.PortalSession
import com.shisan.campuspro.core.network.SessionFlagStore
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * [SyncRepository] 的真实实现：从教务系统抓取 HTML、解析并落库，
 * 同时负责会话过期检测与自动重登。
 *
 * 通知偏好与发布均通过端口接口注入（[NotificationPreferenceStore] / [AcademicUpdateNotifier]），
 * 保持本模块无 Android 依赖、可独立测试。
 */
class JwxtSyncRepository(
    private val directSession: JwxtDirectSession,
    private val portalSession: PortalSession,
    private val credentialsProvider: CredentialsProvider,
    private val sessionFlagStore: SessionFlagStore,
    private val scheduleRepository: ScheduleRepository,
    private val gradesRepository: GradesRepository,
    private val examsRepository: ExamsRepository,
    private val notificationPreferenceStore: NotificationPreferenceStore,
    private val academicUpdateNotifier: AcademicUpdateNotifier,
    private val onScheduleUpdated: () -> Unit,
) : SyncRepository {
    override suspend fun syncAll(autoSync: Boolean): FullSyncResult {
        if (autoSync) {
            val authFailure = ensureSession()
            if (authFailure != null) {
                return FullSyncResult(error = authFailure.userMessage(), failureReason = authFailure)
            }
        }

        val probe = runCatching { probeCurrentTerm() }.getOrElse { throwable ->
            if (throwable is JwxtSessionExpiredException) {
                val reason = SyncFailureReason.SessionExpired
                return FullSyncResult(error = reason.userMessage(), failureReason = reason)
            }
            throw throwable
        }
        val termId = probe.termId
        // 并发执行成绩、考试、课表三个同步操作，各自在 Default 线程池做 Jsoup HTML 解析
        return coroutineScope {
            val gradesDeferred = async {
                runCatching { syncGradesOnce() }.getOrElse { throwable ->
                    if (throwable is JwxtSessionExpiredException) {
                        retryAfterSessionExpired(SyncType.GRADES) { syncGradesOnce() }
                    } else {
                        throwable.toSyncResult(SyncType.GRADES)
                    }
                }
            }

            val examsDeferred = async {
                runCatching { syncExamsOnce(termId) }.getOrElse { throwable ->
                    if (throwable is JwxtSessionExpiredException) {
                        retryAfterSessionExpired(SyncType.EXAMS) { syncExamsOnce(termId) }
                    } else {
                        throwable.toSyncResult(SyncType.EXAMS)
                    }
                }
            }

            val scheduleDeferred = async {
                runCatching { syncCurrentScheduleOnce(termId, probe.coursePageHtml) }.getOrElse { throwable ->
                    if (throwable is JwxtSessionExpiredException) {
                        retryAfterSessionExpired(SyncType.SCHEDULE) { syncCurrentScheduleOnce(termId) }
                    } else {
                        throwable.toSyncResult(SyncType.SCHEDULE)
                    }
                }
            }

            val gradesResult = gradesDeferred.await()
            val examsResult = examsDeferred.await()
            val scheduleResult = scheduleDeferred.await()
            FullSyncResult(schedule = scheduleResult, grades = gradesResult, exams = examsResult)
        }
    }

    override suspend fun syncAllSchedules(): SyncResult {
        return runAuthenticatedSync(SyncType.SCHEDULE) {
            val probe = probeCurrentTerm()
            syncCurrentScheduleOnce(probe.termId, probe.coursePageHtml)
        }
    }

    override suspend fun syncCurrentSchedule(termId: String): SyncResult {
        return runAuthenticatedSync(SyncType.SCHEDULE) { syncCurrentScheduleOnce(termId) }
    }

    private data class TermProbe(val termId: String, val coursePageHtml: String?)

    /**
     * 探测教务系统认定的当前学期：不指定学期请求课表页，取下拉框选中的学期。
     * 学校通常在开学前就已发布新学期课表，按日期推算会滞后，探测失败时才回退到 [TermPolicy]。
     */
    private suspend fun probeCurrentTerm(): TermProbe {
        val fallback = TermPolicy.currentTermId()
        val html = runCatching {
            val page = directSession.getCoursePage("")
            requireJwxtContent(page, "课表")
            page
        }.getOrElse { throwable ->
            if (throwable is JwxtSessionExpiredException) throw throwable
            return TermProbe(fallback, null)
        }
        val selectedTermId = withContext(Dispatchers.Default) { JwxtHtmlParser.parseSelectedTermId(html) }
        return if (selectedTermId != null) TermProbe(selectedTermId, html) else TermProbe(fallback, null)
    }

    private suspend fun syncCurrentScheduleOnce(termId: String, prefetchedHtml: String? = null): SyncResult {
        val html = prefetchedHtml ?: directSession.getCoursePage(termId)
        requireJwxtContent(html, "课表")
        if (!html.contains("kbtable", ignoreCase = true)) {
            error("未获取到课表页面，请稍后重试")
        }
        // 定位本次学期对应的课表：学期切换时新建并激活，避免新学期课程覆盖上学期的课表
        val schedule = resolveScheduleForTerm(termId)
        // Jsoup HTML 解析和课表合并是 CPU 密集操作，放到 Default 线程池
        val parsed = withContext(Dispatchers.Default) {
            val parsedSchedule = JwxtHtmlParser.parseSchedule(html)
            val merged = ScheduleMerger.mergeCourses(
                existing = schedule.courses,
                fetched = parsedSchedule.courses,
                deletedJwxtKeys = schedule.deletedJwxtKeys,
            )
            scheduleRepository.updateCourses(schedule.id, merged)
            parsedSchedule
        }

        // 回填学期下拉框中尚未建表的学期（如首次同步的历史学期，或新学期发布后补建的其他学期）
        val existingTermIds = scheduleRepository.observeSchedules().first().mapNotNull { it.termId }.toSet()
        val termOptions = withContext(Dispatchers.Default) {
            JwxtHtmlParser.parseTermOptions(html)
        }
        for (option in termOptions) {
            if (option.value == termId || option.value in existingTermIds) continue
            val historyHtml = directSession.getCoursePage(option.value)
            requireJwxtContent(historyHtml, "课表")
            if (!historyHtml.contains("kbtable", ignoreCase = true)) continue
            val historyCourses = withContext(Dispatchers.Default) {
                ScheduleMerger.normalizeCourseColors(
                    JwxtHtmlParser.parseSchedule(historyHtml).courses,
                )
            }
            // 空课表不创建
            if (historyCourses.isEmpty()) continue
            val historySchedule = createEmptySchedule(option.value).copy(
                name = option.label,
                termId = option.value,
            )
            scheduleRepository.upsertSchedule(historySchedule)
            scheduleRepository.updateCourses(historySchedule.id, historyCourses)
        }

        onScheduleUpdated()

        return SyncResult(SyncType.SCHEDULE, success = true, count = parsed.courses.size)
    }

    /**
     * 为指定学期定位目标课表：已有同学期课表则复用；学期切换时为新学期新建课表并激活，
     * 上学期的课表原样保留，用户仍可手动切回。
     */
    private suspend fun resolveScheduleForTerm(termId: String): Schedule {
        val schedules = scheduleRepository.observeSchedules().first()
        schedules.firstOrNull { it.termId == termId }?.let { existing ->
            return existing
        }
        val newSchedule = createEmptySchedule(termId)
        // createSchedule 会将新课表设为活跃，避免新学期课表合并进旧学期的活跃课表
        scheduleRepository.createSchedule(newSchedule)
        return newSchedule
    }

    private suspend fun syncGradesOnce(): SyncResult {
        val previous = gradesRepository.observeGrades().first()
        val html = directSession.getGradesPage()
        requireJwxtContent(html, "成绩")
        val grades = withContext(Dispatchers.Default) { JwxtHtmlParser.parseGrades(html) }
        gradesRepository.replaceGrades(grades)
        val settings = notificationPreferenceStore.notificationSettings.first()
        if (settings.gradeBaselineInitialized) {
            if (settings.gradeUpdateEnabled) {
                academicUpdateNotifier.publishGradeUpdates(AcademicUpdateDetector.compareGrades(previous, grades))
            }
        } else {
            notificationPreferenceStore.updateNotificationSettings { it.copy(gradeBaselineInitialized = true) }
        }
        return SyncResult(SyncType.GRADES, success = true, count = grades.size)
    }

    private suspend fun syncExamsOnce(termId: String): SyncResult {
        val previous = examsRepository.observeExams().first()
        val html = directSession.getExamPage(termId)
        requireJwxtContent(html, "考试")
        val exams = withContext(Dispatchers.Default) { JwxtHtmlParser.parseExams(html) }
        examsRepository.replaceExams(exams)
        val settings = notificationPreferenceStore.notificationSettings.first()
        if (settings.examBaselineInitialized) {
            if (settings.examUpdateEnabled) {
                academicUpdateNotifier.publishExamUpdates(AcademicUpdateDetector.compareExams(previous, exams))
            }
        } else {
            notificationPreferenceStore.updateNotificationSettings { it.copy(examBaselineInitialized = true) }
        }
        return SyncResult(SyncType.EXAMS, success = true, count = exams.size)
    }

    private suspend fun runAuthenticatedSync(
        type: SyncType,
        block: suspend () -> SyncResult,
    ): SyncResult {
        val authFailure = ensureSession()
        if (authFailure != null) {
            return SyncResult(type, success = false, count = 0, error = authFailure.userMessage(), failureReason = authFailure)
        }
        return runCatching {
            block()
        }.getOrElse { throwable ->
            if (throwable is JwxtSessionExpiredException) {
                retryAfterSessionExpired(type, block)
            } else {
                throwable.toSyncResult(type)
            }
        }
    }

    private suspend fun retryAfterSessionExpired(
        type: SyncType,
        block: suspend () -> SyncResult,
    ): SyncResult {
        val authFailure = ensureSession(forceRefresh = true)
        if (authFailure != null) {
            val reason = if (authFailure == SyncFailureReason.AuthRequired) {
                SyncFailureReason.SessionExpired
            } else {
                authFailure
            }
            return SyncResult(type, success = false, count = 0, error = reason.userMessage(), failureReason = reason)
        }
        return runCatching { block() }.getOrElse { it.toSyncResult(type) }
    }

    override suspend fun syncByType(type: SyncType): SyncResult {
        return when (type) {
            SyncType.SCHEDULE -> runCurrentSessionSync(SyncType.SCHEDULE) {
                val probe = probeCurrentTerm()
                syncCurrentScheduleOnce(probe.termId, probe.coursePageHtml)
            }
            SyncType.GRADES -> runCurrentSessionSync(SyncType.GRADES) { syncGradesOnce() }
            SyncType.EXAMS -> runCurrentSessionSync(SyncType.EXAMS) {
                syncExamsOnce(probeCurrentTerm().termId)
            }
            SyncType.ALL_SCHEDULES -> syncAllSchedules()
        }
    }

    private suspend fun runCurrentSessionSync(
        type: SyncType,
        block: suspend () -> SyncResult,
    ): SyncResult =
        runCatching {
            block()
        }.getOrElse { throwable ->
            if (throwable is JwxtSessionExpiredException) {
                retryAfterSessionExpired(type, block)
            } else {
                throwable.toSyncResult(type)
            }
        }

    /** 按当前登录模式确保会话有效，返回失败原因（null 表示会话可用）。 */
    private suspend fun ensureSession(forceRefresh: Boolean = false): SyncFailureReason? {
        val autoLoginResult = when (currentLoginMode()) {
            LoginMode.PORTAL -> portalSession.autoLogin(forceRefresh = forceRefresh)
            LoginMode.JWXT_DIRECT -> directSession.autoLogin(forceRefresh = forceRefresh)
            null -> AutoLoginResult.MissingCredentials
        }
        return autoLoginResult.toSyncFailure()
    }

    private suspend fun currentLoginMode(): LoginMode? =
        credentialsProvider.loadCredentials()?.loginMode
            ?: sessionFlagStore.getLoginMode()

    private fun requireJwxtContent(html: String, typeName: String) {
        val lower = html.lowercase()
        if (
            html.contains("统一身份认证") ||
            lower.contains("authserver") ||
            // CAS 登录页特有的元素：密码加密 salt 和登录表单 ID
            lower.contains("pwdencryptsalt") ||
            lower.contains("id=\"casloginform\"")
        ) {
            throw JwxtSessionExpiredException(typeName)
        }
    }
}

internal class JwxtSessionExpiredException(typeName: String) :
    IllegalStateException("教务系统会话已失效，请重新登录后同步$typeName")

private fun Throwable.toSyncResult(type: SyncType): SyncResult {
    val reason = when (this) {
        is JwxtSessionExpiredException -> SyncFailureReason.SessionExpired
        is HttpRequestTimeoutException,
        is SocketTimeoutException,
        -> SyncFailureReason.NetworkTimeout
        is IOException -> SyncFailureReason.NetworkUnavailable
        else -> SyncFailureReason.Unknown
    }
    return SyncResult(
        type = type,
        success = false,
        count = 0,
        error = reason.userMessage(),
        failureReason = reason,
    )
}
