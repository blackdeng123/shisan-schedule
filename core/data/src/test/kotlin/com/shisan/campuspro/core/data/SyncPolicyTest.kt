package com.shisan.campuspro.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SyncPolicyTest {
    @Test
    fun `auto sync is allowed fewer than three times on same day`() {
        val policy = SyncPolicy(maxAutoSyncPerDay = 3)
        val today = LocalDate.of(2026, 6, 17)

        assertTrue(policy.canAutoSync(SyncPolicyState(date = today, count = 0), today))
        assertTrue(policy.canAutoSync(SyncPolicyState(date = today, count = 2), today))
        assertFalse(policy.canAutoSync(SyncPolicyState(date = today, count = 3), today))
    }

    @Test
    fun `record auto sync resets count when date changes`() {
        val policy = SyncPolicy(maxAutoSyncPerDay = 3)
        val today = LocalDate.of(2026, 6, 17)
        val yesterday = LocalDate.of(2026, 6, 16)

        assertEquals(
            SyncPolicyState(date = today, count = 1),
            policy.recordAutoSync(SyncPolicyState(date = yesterday, count = 3), today),
        )
    }
}
