package com.shisan.campuspro.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLoginResultTest {
    @Test
    fun networkErrorKeepsLocalLoginBecauseSavedCredentialsExist() {
        assertTrue(
            AutoLoginResult.NetworkFailure(
                LoginFailureReason.NetworkTimeout,
                "网络连接超时，请稍后重试",
            ).keepsLocalLogin(),
        )
    }

    @Test
    fun authRequiredDoesNotKeepLocalLogin() {
        assertFalse(AutoLoginResult.MissingCredentials.keepsLocalLogin())
        assertFalse(
            AutoLoginResult.CredentialsRejected(
                LoginFailureReason.InvalidCredentials,
                "账号或密码可能已变更，请重新登录",
            ).keepsLocalLogin(),
        )
    }

    @Test
    fun validOrRestoredSessionKeepsLocalLogin() {
        assertTrue(AutoLoginResult.SessionValid.keepsLocalLogin())
        assertTrue(AutoLoginResult.ReloggedIn.keepsLocalLogin())
    }
}
