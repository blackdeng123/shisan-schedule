package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.FullSyncResult
import com.shisan.campuspro.core.model.SyncResult
import com.shisan.campuspro.core.model.SyncType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupSyncCoordinatorTest {
    @Test
    fun `fresh cache does not trigger sync`() = runBlocking {
        val metadata = FakeSyncMetadataStore(initialLastSyncAt = 1_000L)
        val syncRepository = FakeSyncRepository()
        val coordinator = StartupSyncCoordinator(
            syncRepository = syncRepository,
            metadataStore = metadata,
            nowMillis = { 1_000L + 11L * 60L * 60L * 1_000L },
        )

        coordinator.syncIfStale()

        assertEquals(0, syncRepository.syncAllCalls)
        assertEquals(1_000L, metadata.lastSyncAt.value)
    }

    @Test
    fun `sync now ignores fresh cache and updates timestamp on success`() = runBlocking {
        val metadata = FakeSyncMetadataStore(initialLastSyncAt = 1_000L)
        val syncRepository = FakeSyncRepository()
        val coordinator = StartupSyncCoordinator(
            syncRepository = syncRepository,
            metadataStore = metadata,
            nowMillis = { 2_000L },
        )

        coordinator.syncNow()

        assertEquals(1, syncRepository.syncAllCalls)
        assertEquals(false, syncRepository.lastAutoSync)
        assertEquals(2_000L, metadata.lastSyncAt.value)
    }

    @Test
    fun `stale cache triggers one full sync and updates timestamp on success`() = runBlocking {
        val metadata = FakeSyncMetadataStore(initialLastSyncAt = null)
        val syncRepository = FakeSyncRepository()
        val coordinator = StartupSyncCoordinator(
            syncRepository = syncRepository,
            metadataStore = metadata,
            nowMillis = { 50_000L },
        )

        coordinator.syncIfStale()

        assertEquals(1, syncRepository.syncAllCalls)
        assertEquals(true, syncRepository.lastAutoSync)
        assertEquals(50_000L, metadata.lastSyncAt.value)
    }

    @Test
    fun `failed full sync does not update timestamp`() = runBlocking {
        val metadata = FakeSyncMetadataStore(initialLastSyncAt = null)
        val syncRepository = FakeSyncRepository(
            result = FullSyncResult(
                schedule = SyncResult(SyncType.SCHEDULE, success = true, count = 1),
                grades = SyncResult(SyncType.GRADES, success = false, count = 0, error = "failed"),
                exams = SyncResult(SyncType.EXAMS, success = true, count = 1),
            ),
        )
        val coordinator = StartupSyncCoordinator(
            syncRepository = syncRepository,
            metadataStore = metadata,
            nowMillis = { 50_000L },
        )

        coordinator.syncIfStale()

        assertEquals(1, syncRepository.syncAllCalls)
        assertEquals(null, metadata.lastSyncAt.value)
    }
}

private class FakeSyncMetadataStore(initialLastSyncAt: Long?) : SyncMetadataStore {
    val lastSyncAt = MutableStateFlow(initialLastSyncAt)

    override val lastFullSyncAtMillis: Flow<Long?> = lastSyncAt

    override suspend fun setLastFullSyncAtMillis(value: Long) {
        lastSyncAt.value = value
    }
}

private class FakeSyncRepository(
    private val result: FullSyncResult = FullSyncResult(
        schedule = SyncResult(SyncType.SCHEDULE, success = true, count = 1),
        grades = SyncResult(SyncType.GRADES, success = true, count = 1),
        exams = SyncResult(SyncType.EXAMS, success = true, count = 1),
    ),
) : SyncRepository {
    var syncAllCalls = 0
    var lastAutoSync: Boolean? = null

    override suspend fun syncAll(autoSync: Boolean): FullSyncResult {
        syncAllCalls += 1
        lastAutoSync = autoSync
        return result
    }

    override suspend fun syncAllSchedules(): SyncResult =
        SyncResult(SyncType.ALL_SCHEDULES, success = true, count = 0)

    override suspend fun syncCurrentSchedule(termId: String): SyncResult =
        SyncResult(SyncType.SCHEDULE, success = true, count = 0)

    override suspend fun syncByType(type: SyncType): SyncResult =
        SyncResult(type, success = true, count = 0)
}
