package com.shisan.campuspro.feature.schedule

import com.shisan.campuspro.core.data.AuthRepository
import com.shisan.campuspro.core.data.ScheduleRepository
import com.shisan.campuspro.core.data.SyncRepository
import com.shisan.campuspro.core.model.AuthState
import com.shisan.campuspro.core.model.AuthSessionStatus
import com.shisan.campuspro.core.model.ClassTimeSlot
import com.shisan.campuspro.core.model.ClassTimeSeason
import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.FullSyncResult
import com.shisan.campuspro.core.model.LoginMode
import com.shisan.campuspro.core.model.Schedule
import com.shisan.campuspro.core.model.ScheduleDisplaySettings
import com.shisan.campuspro.core.model.SyncFailureReason
import com.shisan.campuspro.core.model.SyncResult
import com.shisan.campuspro.core.model.SyncType
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
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
        val viewModel = ScheduleViewModel(
            scheduleRepository = FakeScheduleRepository(),
            syncRepository = FailingSyncRepository(
                type = SyncType.SCHEDULE,
                failureReason = SyncFailureReason.NetworkTimeout,
            ),
            authRepository = LoggedInAuthRepository(),
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.sync()
        advanceUntilIdle()

        assertEquals("网络连接超时，请检查网络后重试", viewModel.uiState.value.syncMessage)
        collectJob.cancel()
    }

    @Test
    fun `initial state is loading instead of empty schedule`() = runTest(dispatcher) {
        val viewModel = ScheduleViewModel(
            scheduleRepository = FakeScheduleRepository(),
            syncRepository = FailingSyncRepository(
                type = SyncType.SCHEDULE,
                failureReason = SyncFailureReason.NetworkTimeout,
            ),
            authRepository = MutableAuthRepository(AuthSessionStatus.RESTORING),
        )

        assertTrue(viewModel.uiState.value.initialLoading)
    }

    @Test
    fun `authentication changes cannot finish loading before local schedule flows emit`() = runTest(dispatcher) {
        val scheduleRepository = DelayedScheduleRepository()
        val authRepository = MutableAuthRepository(AuthSessionStatus.UNAUTHENTICATED)
        val viewModel = ScheduleViewModel(
            scheduleRepository = scheduleRepository,
            syncRepository = FailingSyncRepository(
                type = SyncType.SCHEDULE,
                failureReason = SyncFailureReason.NetworkTimeout,
            ),
            authRepository = authRepository,
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        authRepository.setStatus(AuthSessionStatus.RESTORING)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.initialLoading)

        val cached = schedule(id = "spring", startDate = "2026-03-09")
        scheduleRepository.emit(listOf(cached), cached)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.initialLoading)
        assertEquals("spring", viewModel.uiState.value.activeSchedule?.id)
        collectJob.cancel()
    }

    @Test
    fun `empty state is available only after local schedule flows emit`() = runTest(dispatcher) {
        val viewModel = ScheduleViewModel(
            scheduleRepository = FakeScheduleRepository(),
            syncRepository = FailingSyncRepository(
                type = SyncType.SCHEDULE,
                failureReason = SyncFailureReason.NetworkTimeout,
            ),
            authRepository = MutableAuthRepository(AuthSessionStatus.RESTORING),
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.initialLoading)
        assertNull(viewModel.uiState.value.activeSchedule)
        collectJob.cancel()
    }

    @Test
    fun `restoring session exposes cached schedule after local load`() = runTest(dispatcher) {
        val cached = schedule(id = "spring", startDate = "2026-03-09")
        val viewModel = ScheduleViewModel(
            scheduleRepository = FakeScheduleRepository(schedules = listOf(cached)),
            syncRepository = FailingSyncRepository(
                type = SyncType.SCHEDULE,
                failureReason = SyncFailureReason.NetworkTimeout,
            ),
            authRepository = MutableAuthRepository(AuthSessionStatus.RESTORING),
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.initialLoading)
        assertEquals("spring", viewModel.uiState.value.activeSchedule?.id)
        collectJob.cancel()
    }

    @Test
    fun `offline authenticated session exposes cache and offline marker`() = runTest(dispatcher) {
        val viewModel = ScheduleViewModel(
            scheduleRepository = FakeScheduleRepository(
                schedules = listOf(schedule(id = "spring", startDate = "2026-03-09")),
            ),
            syncRepository = FailingSyncRepository(
                type = SyncType.SCHEDULE,
                failureReason = SyncFailureReason.NetworkTimeout,
            ),
            authRepository = MutableAuthRepository(AuthSessionStatus.OFFLINE_AUTHENTICATED),
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        advanceUntilIdle()

        assertEquals("spring", viewModel.uiState.value.activeSchedule?.id)
        assertTrue(viewModel.uiState.value.showingOfflineCache)
        collectJob.cancel()
    }

    @Test
    fun `confirmed unauthenticated session hides cached schedule`() = runTest(dispatcher) {
        val authRepository = MutableAuthRepository(AuthSessionStatus.AUTHENTICATED)
        val viewModel = ScheduleViewModel(
            scheduleRepository = FakeScheduleRepository(
                schedules = listOf(schedule(id = "spring", startDate = "2026-03-09")),
            ),
            syncRepository = FailingSyncRepository(
                type = SyncType.SCHEDULE,
                failureReason = SyncFailureReason.NetworkTimeout,
            ),
            authRepository = authRepository,
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        authRepository.setStatus(AuthSessionStatus.UNAUTHENTICATED)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeSchedule)
        assertTrue(viewModel.uiState.value.schedules.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun `selected week defaults to current week when active schedule loads`() = runTest(dispatcher) {
        val scheduleRepository = FakeScheduleRepository(
            schedules = listOf(schedule(id = "spring", startDate = "2026-03-09")),
            activeScheduleId = "spring",
        )
        val viewModel = ScheduleViewModel(
            scheduleRepository = scheduleRepository,
            syncRepository = FailingSyncRepository(
                type = SyncType.SCHEDULE,
                failureReason = SyncFailureReason.NetworkTimeout,
            ),
            authRepository = LoggedInAuthRepository(),
            todayProvider = { LocalDate.of(2026, 3, 23) },
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.currentWeek)
        assertEquals(3, viewModel.uiState.value.selectedWeek)
        collectJob.cancel()
    }

    @Test
    fun `switching schedules resets selected week to new schedule current week`() = runTest(dispatcher) {
        val scheduleRepository = FakeScheduleRepository(
            schedules = listOf(
                schedule(id = "spring", startDate = "2026-03-09"),
                schedule(id = "summer", startDate = "2026-03-16"),
            ),
            activeScheduleId = "spring",
        )
        val viewModel = ScheduleViewModel(
            scheduleRepository = scheduleRepository,
            syncRepository = FailingSyncRepository(
                type = SyncType.SCHEDULE,
                failureReason = SyncFailureReason.NetworkTimeout,
            ),
            authRepository = LoggedInAuthRepository(),
            todayProvider = { LocalDate.of(2026, 3, 23) },
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectWeek(5)
        viewModel.selectSchedule("summer")
        advanceUntilIdle()

        assertEquals("summer", viewModel.uiState.value.activeSchedule?.id)
        assertEquals(2, viewModel.uiState.value.selectedWeek)
        collectJob.cancel()
    }

    @Test
    fun `manual selected week is not overwritten by schedule data refresh`() = runTest(dispatcher) {
        val scheduleRepository = FakeScheduleRepository(
            schedules = listOf(schedule(id = "spring", startDate = "2026-03-09")),
            activeScheduleId = "spring",
        )
        val viewModel = ScheduleViewModel(
            scheduleRepository = scheduleRepository,
            syncRepository = FailingSyncRepository(
                type = SyncType.SCHEDULE,
                failureReason = SyncFailureReason.NetworkTimeout,
            ),
            authRepository = LoggedInAuthRepository(),
            todayProvider = { LocalDate.of(2026, 3, 23) },
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectWeek(5)
        scheduleRepository.upsertSchedule(schedule(id = "spring", startDate = "2026-03-16"))
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.selectedWeek)
        collectJob.cancel()
    }

    @Test
    fun `term phase reflects upcoming term before its start date`() = runTest(dispatcher) {
        val viewModel = ScheduleViewModel(
            scheduleRepository = FakeScheduleRepository(
                schedules = listOf(schedule(id = "fall", startDate = "2026-09-07")),
                activeScheduleId = "fall",
            ),
            syncRepository = FailingSyncRepository(
                type = SyncType.SCHEDULE,
                failureReason = SyncFailureReason.NetworkTimeout,
            ),
            authRepository = LoggedInAuthRepository(),
            todayProvider = { LocalDate.of(2026, 8, 27) },
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.termPhase is TermPhase.NotStarted)
        collectJob.cancel()
    }

    @Test
    fun `term phase marks ended term after its last week`() = runTest(dispatcher) {
        val viewModel = ScheduleViewModel(
            scheduleRepository = FakeScheduleRepository(
                schedules = listOf(schedule(id = "spring", startDate = "2026-03-09")),
                activeScheduleId = "spring",
            ),
            syncRepository = FailingSyncRepository(
                type = SyncType.SCHEDULE,
                failureReason = SyncFailureReason.NetworkTimeout,
            ),
            authRepository = LoggedInAuthRepository(),
            todayProvider = { LocalDate.of(2026, 8, 27) },
        )
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(TermPhase.Ended, viewModel.uiState.value.termPhase)
        collectJob.cancel()
    }
}

private class LoggedInAuthRepository : AuthRepository {
    override val authState: Flow<AuthState> = MutableStateFlow(AuthState(true, "20260001", "用户"))
    override suspend fun login(username: String, password: String, mode: LoginMode, rememberCredentials: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun autoLogin(): Boolean = true
    override suspend fun logout() = Unit
}

private class MutableAuthRepository(initialStatus: AuthSessionStatus) : AuthRepository {
    private val state = MutableStateFlow(
        AuthState(
            isLoggedIn = initialStatus == AuthSessionStatus.AUTHENTICATED,
            studentId = "20260001",
            studentName = "用户",
            sessionStatus = initialStatus,
        ),
    )
    override val authState: Flow<AuthState> = state

    fun setStatus(status: AuthSessionStatus) {
        state.value = state.value.copy(
            isLoggedIn = status == AuthSessionStatus.AUTHENTICATED,
            sessionStatus = status,
        )
    }

    override suspend fun login(username: String, password: String, mode: LoginMode, rememberCredentials: Boolean) =
        Result.success(Unit)
    override suspend fun autoLogin(): Boolean = true
    override suspend fun logout() = Unit
}

private class DelayedScheduleRepository(
    private val delegate: FakeScheduleRepository = FakeScheduleRepository(),
) : ScheduleRepository by delegate {
    private val schedules = MutableSharedFlow<List<Schedule>>()
    private val activeSchedule = MutableSharedFlow<Schedule?>()

    override fun observeSchedules(): Flow<List<Schedule>> = schedules
    override fun observeActiveSchedule(): Flow<Schedule?> = activeSchedule

    suspend fun emit(allSchedules: List<Schedule>, active: Schedule?) {
        schedules.emit(allSchedules)
        activeSchedule.emit(active)
    }
}

private class FakeScheduleRepository(
    schedules: List<Schedule> = emptyList(),
    activeScheduleId: String? = schedules.firstOrNull()?.id,
) : ScheduleRepository {
    private val schedulesFlow = MutableStateFlow(schedules)
    private val activeScheduleIdFlow = MutableStateFlow(activeScheduleId)

    override fun observeSchedules(): Flow<List<Schedule>> = schedulesFlow
    override fun observeActiveSchedule(): Flow<Schedule?> =
        combine(schedulesFlow, activeScheduleIdFlow) { schedules, activeScheduleId ->
            schedules.firstOrNull { it.id == activeScheduleId } ?: schedules.firstOrNull()
        }

    override suspend fun selectSchedule(scheduleId: String) {
        activeScheduleIdFlow.value = scheduleId
    }

    override suspend fun createSchedule(schedule: Schedule) {
        upsertSchedule(schedule)
        activeScheduleIdFlow.value = schedule.id
    }

    override suspend fun deleteSchedule(scheduleId: String) {
        schedulesFlow.value = schedulesFlow.value.filterNot { it.id == scheduleId }
        if (activeScheduleIdFlow.value == scheduleId) {
            activeScheduleIdFlow.value = schedulesFlow.value.firstOrNull()?.id
        }
    }

    override suspend fun upsertSchedule(schedule: Schedule) {
        schedulesFlow.value = schedulesFlow.value
            .filterNot { it.id == schedule.id } + schedule
    }

    override suspend fun updateScheduleSettings(scheduleId: String, settings: ScheduleDisplaySettings) = Unit
    override suspend fun upsertCourse(scheduleId: String, course: Course) = Unit
    override suspend fun deleteCourse(scheduleId: String, courseId: String) = Unit
    override suspend fun updateCourses(scheduleId: String, courses: List<Course>) = Unit
    override suspend fun replaceClassTimeSlots(
        scheduleId: String,
        season: ClassTimeSeason,
        slots: List<ClassTimeSlot>,
    ) = Unit
    override suspend fun selectClassTimeSeason(scheduleId: String, season: ClassTimeSeason) = Unit
    override suspend fun clearAll() = Unit
}

private fun schedule(
    id: String,
    startDate: String,
    name: String = id,
) = Schedule(
    id = id,
    name = name,
    courses = emptyList(),
    startDate = startDate,
    totalWeeks = 20,
    dataSources = emptyList(),
    deletedJwxtKeys = emptySet(),
)

private class FailingSyncRepository(
    private val type: SyncType,
    private val failureReason: SyncFailureReason,
) : SyncRepository {
    override suspend fun syncAll(autoSync: Boolean): FullSyncResult =
        FullSyncResult(error = "同步失败", failureReason = failureReason)

    override suspend fun syncAllSchedules(): SyncResult =
        SyncResult(type, false, 0, "同步失败", failureReason)

    override suspend fun syncCurrentSchedule(termId: String): SyncResult =
        SyncResult(type, false, 0, "同步失败", failureReason)

    override suspend fun syncByType(type: SyncType): SyncResult =
        SyncResult(type, false, 0, "同步失败", failureReason)
}
