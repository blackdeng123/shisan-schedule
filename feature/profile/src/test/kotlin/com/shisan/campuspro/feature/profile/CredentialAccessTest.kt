package com.shisan.campuspro.feature.profile

import com.shisan.campuspro.core.model.AuthState
import com.shisan.campuspro.core.model.LoginMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialAccessTest {
    @Test
    fun `只有已登录的门户会话可以直接进入可信电子凭证`() {
        assertEquals(
            CredentialEntryDecision.Open,
            credentialEntryDecision(
                AuthState(true, "20260001", "用户", LoginMode.PORTAL),
            ),
        )
        assertEquals(
            CredentialEntryDecision.RequirePortalLogin,
            credentialEntryDecision(
                AuthState(true, "20260001", "用户", LoginMode.JWXT_DIRECT),
            ),
        )
        assertEquals(
            CredentialEntryDecision.RequirePortalLogin,
            credentialEntryDecision(AuthState(false, "", "", LoginMode.PORTAL)),
        )
        // 即使已登录，loginMode 为 null 时也应要求门户登录
        assertEquals(
            CredentialEntryDecision.RequirePortalLogin,
            credentialEntryDecision(AuthState(true, "20260001", "用户", loginMode = null)),
        )
    }

    @Test
    fun `待进入凭证页只在门户登录成功后续接`() {
        assertEquals(true, shouldResumeCredential(true, LoginMode.PORTAL))
        assertEquals(false, shouldResumeCredential(true, LoginMode.JWXT_DIRECT))
        assertEquals(false, shouldResumeCredential(false, LoginMode.PORTAL))
    }
}
