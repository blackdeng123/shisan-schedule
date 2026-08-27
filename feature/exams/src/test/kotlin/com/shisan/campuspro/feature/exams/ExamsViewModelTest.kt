package com.shisan.campuspro.feature.exams

import com.shisan.campuspro.core.data.AuthRepository
import com.shisan.campuspro.core.data.ExamsRepository
import com.shisan.campuspro.core.data.SyncRepository
import com.shisan.campuspro.core.model.AuthState
import com.shisan.campuspro.core.model.Exam
import com.shisan.campuspro.core.model.FullSyncResult
import com.shisan.campuspro.core.model.LoginMode
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExamsViewModelTest {
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
    fun `sync requests exams and resets syncing state`() = runTest(dispatcher) {
        val syncRepository = RecordingSyncRepository()
        val viewModel = ExamsViewModel(
            repository = FakeExamsRepository(),
            syncRepository = syncRepository,
            authRepository = LoggedInAuthRepository(),
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.sync()
        advanceUntilIdle()

        assertEquals(SyncType.EXAMS, syncRepository.lastType)
        assertFalse(viewModel.uiState.value.syncing)
        collectJob.cancel()
    }
}

private class FakeExamsRepository : ExamsRepository {
    override fun observeExams(): Flow<List<Exam>> = MutableStateFlow(emptyList())
    override suspend fun replaceExams(exams: List<Exam>) = Unit
    override suspend fun clearAll() = Unit
}

private class LoggedInAuthRepository : AuthRepository {
    override val authState: Flow<AuthState> = MutableStateFlow(AuthState(true, "20260001", "用户"))
    override suspend fun login(username: String, password: String, mode: LoginMode, rememberCredentials: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun autoLogin(): Boolean = true
    override suspend fun logout() = Unit
}

private class RecordingSyncRepository : SyncRepository {
    var lastType: SyncType? = null

    override suspend fun syncAll(autoSync: Boolean): FullSyncResult = FullSyncResult()

    override suspend fun syncAllSchedules(): SyncResult =
        SyncResult(SyncType.ALL_SCHEDULES, success = true, count = 0)

    override suspend fun syncCurrentSchedule(termId: String): SyncResult =
        SyncResult(SyncType.SCHEDULE, success = true, count = 0)

    override suspend fun syncByType(type: SyncType): SyncResult {
        lastType = type
        return SyncResult(type, success = true, count = 0)
    }
}
