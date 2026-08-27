package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.Credentials
import com.shisan.campuspro.core.model.LoginMode

/** 会话测试共用假实现，供 JwxtDirectSessionTest / PortalSessionTest 使用。 */
internal class FakeCredentialsProvider(
    private var credentials: Credentials? = null,
) : CredentialsProvider {
    var credentialsCleared = false
    var savedCredentials: Credentials? = null

    override suspend fun loadCredentials(): Credentials? = credentials

    override suspend fun saveCredentials(credentials: Credentials) {
        this.credentials = credentials
        savedCredentials = credentials
    }

    override suspend fun clearCredentials() {
        credentialsCleared = true
        credentials = null
    }
}

internal class FakeSessionFlagStore(
    hasSession: Boolean = true,
    loginMode: LoginMode? = null,
) : SessionFlagStore {
    private var hasSession = hasSession
    private var loginMode = loginMode

    override suspend fun hasSession(): Boolean = hasSession

    override suspend fun setHasSession(value: Boolean) {
        hasSession = value
    }

    override suspend fun getLoginMode(): LoginMode? = loginMode

    override suspend fun setLoginMode(mode: LoginMode?) {
        loginMode = mode
    }
}
