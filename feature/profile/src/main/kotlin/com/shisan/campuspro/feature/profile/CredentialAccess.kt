package com.shisan.campuspro.feature.profile

import com.shisan.campuspro.core.model.AuthState
import com.shisan.campuspro.core.model.LoginMode

enum class CredentialEntryDecision {
    Open,
    RequirePortalLogin,
}

fun credentialEntryDecision(authState: AuthState): CredentialEntryDecision =
    if (authState.isLoggedIn && authState.loginMode == LoginMode.PORTAL) {
        CredentialEntryDecision.Open
    } else {
        CredentialEntryDecision.RequirePortalLogin
    }

fun shouldResumeCredential(pending: Boolean, loginMode: LoginMode): Boolean =
    pending && loginMode == LoginMode.PORTAL
