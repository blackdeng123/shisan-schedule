package com.shisan.campuspro.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {
    @Test
    fun `manual check always bypasses interval`() {
        assertTrue(UpdateCheckPolicy.shouldCheck(manual = true, nowMillis = 1_000, lastCheckMillis = 999))
    }

    @Test
    fun `automatic check waits twenty four hours`() {
        val now = 100L * 60 * 60 * 1000

        assertFalse(
            UpdateCheckPolicy.shouldCheck(
                manual = false,
                nowMillis = now,
                lastCheckMillis = now - UpdateCheckPolicy.INTERVAL_MILLIS + 1,
            ),
        )
        assertTrue(
            UpdateCheckPolicy.shouldCheck(
                manual = false,
                nowMillis = now,
                lastCheckMillis = now - UpdateCheckPolicy.INTERVAL_MILLIS,
            ),
        )
    }
}
