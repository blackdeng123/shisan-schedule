package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.FullSyncResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SyncFreshnessPolicy {
    const val CACHE_TTL_MILLIS: Long = 12L * 60L * 60L * 1_000L

    fun isStale(lastSyncAtMillis: Long?, nowMillis: Long): Boolean =
        lastSyncAtMillis == null || nowMillis - lastSyncAtMillis >= CACHE_TTL_MILLIS
}

interface SyncMetadataStore {
    val lastFullSyncAtMillis: Flow<Long?>
    suspend fun setLastFullSyncAtMillis(value: Long)
}

class StartupSyncCoordinator(
    private val syncRepository: SyncRepository,
    private val metadataStore: SyncMetadataStore,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()

    suspend fun syncIfStale() {
        mutex.withLock {
            val now = nowMillis()
            val lastSyncAt = metadataStore.lastFullSyncAtMillis.first()
            if (!SyncFreshnessPolicy.isStale(lastSyncAt, now)) return

            runFullSyncAndRecord(now, autoSync = true)
        }
    }

    suspend fun syncNow(): FullSyncResult? =
        mutex.withLock {
            runFullSyncAndRecord(nowMillis(), autoSync = false)
        }

    private suspend fun runFullSyncAndRecord(now: Long, autoSync: Boolean): FullSyncResult? {
        val result = try {
            syncRepository.syncAll(autoSync = autoSync)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return null
        }
        if (result.isFullSuccess()) {
            metadataStore.setLastFullSyncAtMillis(now)
        }
        return result
    }
}

private fun FullSyncResult.isFullSuccess(): Boolean {
    if (error != null) return false
    return listOfNotNull(schedule, grades, exams).all { it.success }
}
