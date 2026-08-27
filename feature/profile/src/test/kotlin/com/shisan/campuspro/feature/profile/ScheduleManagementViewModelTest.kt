package com.shisan.campuspro.feature.profile

import com.shisan.campuspro.core.data.ScheduleRepository
import com.shisan.campuspro.core.model.ClassTimeSlot
import com.shisan.campuspro.core.model.ClassTimeSeason
import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.Schedule
import com.shisan.campuspro.core.model.ScheduleDisplaySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
class ScheduleManagementViewModelTest {
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
    fun `create schedule adds and selects new schedule`() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(listOf(schedule("one")))
        val viewModel = ScheduleManagementViewModel(repository)
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.createSchedule("新课表")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.schedules.size)
        assertEquals("新课表", viewModel.uiState.value.activeSchedule?.name)
        collectJob.cancel()
    }

    @Test
    fun `schedule management leaves loading state after repository emits`() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(listOf(schedule("one")))
        val viewModel = ScheduleManagementViewModel(repository)
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.schedules.size)
        collectJob.cancel()
    }

    @Test
    fun `select schedule changes active schedule`() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(listOf(schedule("one"), schedule("two")))
        val viewModel = ScheduleManagementViewModel(repository)
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectSchedule("two")
        advanceUntilIdle()

        assertEquals("two", viewModel.uiState.value.activeSchedule?.id)
        collectJob.cancel()
    }

    @Test
    fun `rename schedule updates schedule name`() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(listOf(schedule("one")))
        val viewModel = ScheduleManagementViewModel(repository)
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.renameSchedule("one", "第一学期")
        advanceUntilIdle()

        assertEquals("第一学期", viewModel.uiState.value.schedules.first().name)
        collectJob.cancel()
    }

    @Test
    fun `delete last schedule is blocked with message`() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(listOf(schedule("one")))
        val viewModel = ScheduleManagementViewModel(repository)
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.deleteSchedule("one")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.schedules.size)
        assertEquals("至少保留一个课表", viewModel.uiState.value.message?.text)
        collectJob.cancel()
    }
}

private class FakeScheduleRepository(
    schedules: List<Schedule>,
) : ScheduleRepository {
    private val schedulesFlow = MutableStateFlow(schedules)
    private val activeScheduleIdFlow = MutableStateFlow(schedules.firstOrNull()?.id)

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

private fun schedule(id: String) = Schedule(
    id = id,
    name = id,
    courses = emptyList(),
    startDate = "2026-03-09",
    totalWeeks = 20,
    dataSources = emptyList(),
    deletedJwxtKeys = emptySet(),
)
