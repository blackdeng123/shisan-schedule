package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.AuthAutoLoginResult
import com.shisan.campuspro.core.model.AuthSessionStatus
import com.shisan.campuspro.core.model.SyncFailureReason
import com.shisan.campuspro.core.model.canAccessCachedData
import com.shisan.campuspro.core.model.canAccessRemoteData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionStatusTest {
    @Test
    fun `successful auto login enables remote and cached data`() {
        val status = AuthAutoLoginResult.Success.toSessionStatus()

        assertEquals(AuthSessionStatus.AUTHENTICATED, status)
        assertTrue(status.canAccessRemoteData)
        assertTrue(status.canAccessCachedData)
    }

    @Test
    fun `network failure keeps cached data without enabling remote data`() {
        val status = AuthAutoLoginResult.NetworkFailure(
            failureReason = SyncFailureReason.NetworkTimeout,
            message = "网络连接超时",
        ).toSessionStatus()

        assertEquals(AuthSessionStatus.OFFLINE_AUTHENTICATED, status)
        assertFalse(status.canAccessRemoteData)
        assertTrue(status.canAccessCachedData)
    }

    @Test
    fun `missing or rejected credentials revoke cached data access`() {
        val rejected = AuthAutoLoginResult.CredentialsRejected(
            failureReason = SyncFailureReason.CredentialsRejected,
            message = "登录失效",
        )

        assertEquals(AuthSessionStatus.UNAUTHENTICATED, AuthAutoLoginResult.MissingCredentials.toSessionStatus())
        assertEquals(AuthSessionStatus.UNAUTHENTICATED, rejected.toSessionStatus())
        assertFalse(rejected.toSessionStatus().canAccessCachedData)
    }
}
