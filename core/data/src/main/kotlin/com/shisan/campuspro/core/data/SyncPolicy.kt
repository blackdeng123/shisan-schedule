package com.shisan.campuspro.core.data

import java.time.LocalDate

data class SyncPolicyState(
    val date: LocalDate,
    val count: Int,
)

class SyncPolicy(
    private val maxAutoSyncPerDay: Int,
) {
    fun canAutoSync(state: SyncPolicyState?, today: LocalDate = LocalDate.now()): Boolean {
        if (state == null) return true
        if (state.date != today) return true
        return state.count < maxAutoSyncPerDay
    }

    fun recordAutoSync(state: SyncPolicyState?, today: LocalDate = LocalDate.now()): SyncPolicyState {
        if (state == null || state.date != today) {
            return SyncPolicyState(date = today, count = 1)
        }
        return state.copy(count = state.count + 1)
    }
}
