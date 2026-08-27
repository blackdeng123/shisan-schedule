package com.shisan.campuspro.feature.grades

import com.shisan.campuspro.core.data.AuthRepository
import com.shisan.campuspro.core.data.GradesRepository
import com.shisan.campuspro.core.data.SyncRepository
import com.shisan.campuspro.core.model.AuthState
import com.shisan.campuspro.core.model.FullSyncResult
import com.shisan.campuspro.core.model.Grade
import com.shisan.campuspro.core.model.LoginMode
import com.shisan.campuspro.core.model.SyncFailureReason
import com.shisan.campuspro.core.model.SyncResult
import com.shisan.campuspro.core.model.SyncType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GradesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sync failure is exposed in ui state`() = runTest(dispatcher) {
        val viewModel = GradesViewModel(
            repository = FakeGradesRepository(),
            syncRepository = FailingSyncRepository(SyncFailureReason.CredentialsRejected),
            authRepository = LoggedInAuthRepository(),
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.sync()
        advanceUntilIdle()

        assertEquals("账号或密码可能已变更，请重新登录", viewModel.uiState.value.syncMessage)
        collectJob.cancel()
    }

    @Test
    fun `defaults to latest semester grades`() = runTest(dispatcher) {
        val viewModel = GradesViewModel(
            repository = FakeGradesRepository(
                listOf(
                    grade(id = "old", name = "高等数学", semester = "2025-2026-1"),
                    grade(id = "new", name = "大学英语", semester = "2025-2026-2"),
                ),
            ),
            syncRepository = FailingSyncRepository(SyncFailureReason.CredentialsRejected),
            authRepository = LoggedInAuthRepository(),
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("2025-2026-2", viewModel.uiState.value.selectedSemester)
        assertEquals(listOf("new"), viewModel.uiState.value.visibleGrades.map { it.id })
        // 学期筛选列表按时间倒序（新学期在最前，显示在最上面）
        assertEquals(
            listOf("2025-2026-2", "2025-2026-1"),
            viewModel.uiState.value.semesters,
        )
        collectJob.cancel()
    }

    @Test
    fun `selecting all semesters shows all grades`() = runTest(dispatcher) {
        val viewModel = GradesViewModel(
            repository = FakeGradesRepository(
                listOf(
                    grade(id = "old", name = "高等数学", semester = "2025-2026-1"),
                    grade(id = "new", name = "大学英语", semester = "2025-2026-2"),
                ),
            ),
            syncRepository = FailingSyncRepository(SyncFailureReason.CredentialsRejected),
            authRepository = LoggedInAuthRepository(),
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectSemester(null)
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.selectedSemester)
        assertEquals(listOf("old", "new"), viewModel.uiState.value.visibleGrades.map { it.id })
        collectJob.cancel()
    }

    @Test
    fun `course query filters visible grades by course name`() = runTest(dispatcher) {
        val viewModel = GradesViewModel(
            repository = FakeGradesRepository(
                listOf(
                    grade(id = "math", name = "高等数学", semester = "2025-2026-2"),
                    grade(id = "english", name = "大学英语", semester = "2025-2026-2"),
                    grade(id = "old", name = "线性代数", semester = "2025-2026-1"),
                ),
            ),
            syncRepository = FailingSyncRepository(SyncFailureReason.CredentialsRejected),
            authRepository = LoggedInAuthRepository(),
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.updateCourseQuery("数学")
        advanceUntilIdle()

        assertEquals(listOf("math"), viewModel.uiState.value.visibleGrades.map { it.id })
        collectJob.cancel()
    }
}

private class FakeGradesRepository(
    grades: List<Grade> = emptyList(),
) : GradesRepository {
    private val gradesFlow = MutableStateFlow(grades)
    override fun observeGrades(): Flow<List<Grade>> = gradesFlow
    override suspend fun replaceGrades(grades: List<Grade>) = Unit
    override suspend fun clearAll() = Unit
}

private fun grade(
    id: String,
    name: String,
    semester: String,
) = Grade(
    id = id,
    name = name,
    type = "必修",
    credits = 2.0,
    score = 90.0,
    scoreText = "90",
    gpa = 4.0,
    semester = semester,
)

private class LoggedInAuthRepository : AuthRepository {
    override val authState: Flow<AuthState> = MutableStateFlow(AuthState(true, "20260001", "用户"))
    override suspend fun login(username: String, password: String, mode: LoginMode, rememberCredentials: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun autoLogin(): Boolean = true
    override suspend fun logout() = Unit
}

private class FailingSyncRepository(
    private val failureReason: SyncFailureReason,
) : SyncRepository {
    override suspend fun syncAll(autoSync: Boolean): FullSyncResult =
        FullSyncResult(error = "同步失败", failureReason = failureReason)

    override suspend fun syncAllSchedules(): SyncResult =
        SyncResult(SyncType.ALL_SCHEDULES, false, 0, "同步失败", failureReason)

    override suspend fun syncCurrentSchedule(termId: String): SyncResult =
        SyncResult(SyncType.SCHEDULE, false, 0, "同步失败", failureReason)

    override suspend fun syncByType(type: SyncType): SyncResult =
        SyncResult(type, false, 0, "同步失败", failureReason)
}
