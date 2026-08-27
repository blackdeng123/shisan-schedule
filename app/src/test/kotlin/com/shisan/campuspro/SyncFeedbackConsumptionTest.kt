package com.shisan.campuspro

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SyncFeedbackConsumptionTest {
    @Test
    fun matchingIdConsumesCurrentFeedback() {
        val feedback = SyncFeedback(id = 2L, message = "同步完成")

        assertNull(consumeSyncFeedback(feedback, consumedId = 2L))
    }

    @Test
    fun staleIdDoesNotConsumeNewerFeedback() {
        val feedback = SyncFeedback(id = 3L, message = "新的同步结果")

        assertSame(feedback, consumeSyncFeedback(feedback, consumedId = 2L))
    }
}
