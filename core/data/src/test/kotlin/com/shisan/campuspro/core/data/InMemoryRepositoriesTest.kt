package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.ClassTimeSlot
import com.shisan.campuspro.core.model.ClassTimeSeason
import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import com.shisan.campuspro.core.model.LoginMode
import com.shisan.campuspro.core.model.Schedule
import com.shisan.campuspro.core.model.ScheduleDisplaySettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryRepositoriesTest {
    @Test
    fun `auth repository exposes selected login mode and clears it on logout`() = runTest {
        val repository = InMemoryAuthRepository()

        repository.login("20260001", "secret", LoginMode.PORTAL)
        assertEquals(LoginMode.PORTAL, repository.authState.first().loginMode)

        repository.logout()
        assertNull(repository.authState.first().loginMode)
    }

    @Test
    fun `schedule repository starts without demo schedule`() = runTest {
        val repository = InMemoryScheduleRepository()

        assertTrue(repository.observeSchedules().first().isEmpty())
        assertNull(repository.observeActiveSchedule().first())
    }

    @Test
    fun `exams repository starts without demo exams`() = runTest {
        val repository = InMemoryExamsRepository()

        assertTrue(repository.observeExams().first().isEmpty())
    }

    @Test
    fun `default sync creates current term schedule when none exists`() = runTest {
        val scheduleRepository = InMemoryScheduleRepository()
        val syncRepository = DefaultSyncRepository(
            scheduleRepository = scheduleRepository,
            gradesRepository = InMemoryGradesRepository(),
            examsRepository = InMemoryExamsRepository(),
        )

        val result = syncRepository.syncCurrentSchedule("2026-2027-1")
        val activeSchedule = scheduleRepository.observeActiveSchedule().first()

        assertTrue(result.success)
        assertEquals("2026-2027-1", activeSchedule?.termId)
        assertEquals("2026-2027 第一学期", activeSchedule?.name)
        assertTrue(activeSchedule?.courses?.isEmpty() == true)
        assertNotNull(activeSchedule)
    }

    @Test
    fun `schedule repository supports course crud settings and class time slots`() = runTest {
        val repository = InMemoryScheduleRepository()
        val schedule = schedule(id = "schedule-a")
        val course = course(id = "course-a", name = "高等数学")

        repository.createSchedule(schedule)
        repository.upsertCourse(schedule.id, course)
        repository.updateScheduleSettings(
            scheduleId = schedule.id,
            settings = ScheduleDisplaySettings(showWeekend = false, backgroundUri = "content://background"),
        )
        repository.replaceClassTimeSlots(
            scheduleId = schedule.id,
            season = ClassTimeSeason.SUMMER,
            slots = listOf(ClassTimeSlot(index = 1, startTime = "08:30", endTime = "09:15")),
        )

        val updated = repository.observeActiveSchedule().first()
        assertEquals(listOf(course), updated?.courses)
        assertEquals(false, updated?.displaySettings?.showWeekend)
        assertEquals("content://background", updated?.displaySettings?.backgroundUri)
        assertEquals("08:30", updated?.classTimeSlots?.firstOrNull()?.startTime)

        repository.deleteCourse(schedule.id, course.id)

        assertTrue(repository.observeActiveSchedule().first()?.courses?.isEmpty() == true)
    }

    @Test
    fun `summer and winter class times are stored independently and switching is immediate`() = runTest {
        val repository = InMemoryScheduleRepository()
        repository.createSchedule(schedule(id = "schedule-a"))

        repository.replaceClassTimeSlots(
            scheduleId = "schedule-a",
            season = ClassTimeSeason.WINTER,
            slots = listOf(ClassTimeSlot(1, "07:30", "08:15")),
        )
        repository.selectClassTimeSeason("schedule-a", ClassTimeSeason.WINTER)

        val winter = repository.observeActiveSchedule().first()
        assertEquals(ClassTimeSeason.WINTER, winter?.classTimeSeason)
        assertEquals("07:30", winter?.classTimeSlots?.first()?.startTime)
        assertEquals("08:00", winter?.summerClassTimeSlots?.first()?.startTime)
    }

    @Test
    fun `winter defaults move slot five and later thirty minutes earlier`() {
        val winter = com.shisan.campuspro.core.model.DefaultWinterClassTimeSlots

        assertEquals("11:05", winter[3].startTime)
        assertEquals("14:30", winter[4].startTime)
        assertEquals("19:00", winter[8].startTime)
    }

    @Test
    fun `adding a slot keeps duration and inserts ten minute break`() {
        val slots = listOf(ClassTimeSlot(10, "20:25", "21:10"))

        val added = com.shisan.campuspro.core.model.appendClassTimeSlot(slots)

        assertEquals(ClassTimeSlot(11, "21:20", "22:05"), added.last())
    }

    @Test
    fun `slot removal keeps at least ten and never removes a used slot`() {
        val twelveSlots = (1..12).map { ClassTimeSlot(it, "08:00", "08:45") }

        assertTrue(com.shisan.campuspro.core.model.canRemoveLastClassTimeSlot(twelveSlots, maxUsedSlot = 11))
        assertEquals(
            false,
            com.shisan.campuspro.core.model.canRemoveLastClassTimeSlot(twelveSlots, maxUsedSlot = 12),
        )
        assertEquals(
            false,
            com.shisan.campuspro.core.model.canRemoveLastClassTimeSlot(twelveSlots.take(10), maxUsedSlot = 0),
        )
    }

    @Test
    fun `deleting active schedule selects next available schedule`() = runTest {
        val repository = InMemoryScheduleRepository()
        repository.createSchedule(schedule(id = "schedule-a"))
        repository.createSchedule(schedule(id = "schedule-b"))

        repository.deleteSchedule("schedule-b")

        assertEquals("schedule-a", repository.observeActiveSchedule().first()?.id)
    }

    private fun schedule(id: String): Schedule =
        Schedule(
            id = id,
            name = id,
            courses = emptyList(),
            startDate = "",
            totalWeeks = 20,
            dataSources = emptyList(),
            deletedJwxtKeys = emptySet(),
        )

    private fun course(id: String, name: String): Course =
        Course(
            id = id,
            name = name,
            location = "一教101",
            teacher = "张老师",
            day = 1,
            startSlot = 1,
            endSlot = 2,
            color = "#72A5F2",
            weeks = listOf(1, 2, 3),
            source = CourseSource.MANUAL,
            jwxtKey = "",
        )
}
