package com.shisan.campuspro.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFreshnessPolicyTest {
    @Test
    fun `never synced is stale`() {
        assertTrue(SyncFreshnessPolicy.isStale(lastSyncAtMillis = null, nowMillis = 100_000L))
    }

    @Test
    fun `eleven hours and fifty nine minutes is fresh`() {
        val now = 12L * 60L * 60L * 1_000L
        val lastSync = 1L * 60L * 1_000L

        assertFalse(SyncFreshnessPolicy.isStale(lastSyncAtMillis = lastSync, nowMillis = now))
    }

    @Test
    fun `twelve hours is stale`() {
        val now = 12L * 60L * 60L * 1_000L

        assertTrue(SyncFreshnessPolicy.isStale(lastSyncAtMillis = 0L, nowMillis = now))
    }

    @Test
    fun `more than twelve hours is stale`() {
        val now = 12L * 60L * 60L * 1_000L + 1L

        assertTrue(SyncFreshnessPolicy.isStale(lastSyncAtMillis = 0L, nowMillis = now))
    }
}
