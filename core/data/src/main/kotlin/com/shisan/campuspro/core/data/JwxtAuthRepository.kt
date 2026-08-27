package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.AuthAutoLoginResult
import com.shisan.campuspro.core.model.AuthSessionStatus
import com.shisan.campuspro.core.model.AuthState
import com.shisan.campuspro.core.model.LoginMode
import com.shisan.campuspro.core.model.PortalWebSessionResult
import com.shisan.campuspro.core.model.SyncFailureReason
import com.shisan.campuspro.core.model.canAccessRemoteData
import com.shisan.campuspro.core.network.AutoLoginResult
import com.shisan.campuspro.core.network.CredentialsProvider
import com.shisan.campuspro.core.network.JwxtDirectSession
import com.shisan.campuspro.core.network.LoginFailureReason
import com.shisan.campuspro.core.network.LoginResult
import com.shisan.campuspro.core.network.PortalSession
import com.shisan.campuspro.core.network.SessionFlagStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [AuthRepository] 的真实实现，协调直连与门户两套独立会话。
 *
 * 登录按 [LoginMode] 分发给对应的会话；自动登录按当前登录模式恢复会话。
 * WebView Cookie 清理通过 [webCookieCleaner] 端口注入，保持模块无 Android 依赖。
 */
class JwxtAuthRepository(
    private val directSession: JwxtDirectSession,
    private val portalSession: PortalSession,
    private val credentialsProvider: CredentialsProvider,
    private val sessionFlagStore: SessionFlagStore,
    private val webCookieCleaner: WebCookieCleaner,
) : AuthRepository {
    private val state = MutableStateFlow(
        AuthState(
            isLoggedIn = false,
            studentId = "",
            studentName = "",
            sessionResolved = false,
        ),
    )
    override val authState: Flow<AuthState> = state

    override suspend fun login(
        username: String,
        password: String,
        mode: LoginMode,
        rememberCredentials: Boolean,
    ): Result<Unit> {
        val result = when (mode) {
            LoginMode.PORTAL -> portalSession.login(username, password, rememberCredentials = rememberCredentials)
            LoginMode.JWXT_DIRECT -> directSession.login(username, password, rememberCredentials = rememberCredentials)
        }
        return when (result) {
            LoginResult.Success -> {
                state.value = AuthState(
                    isLoggedIn = true,
                    studentId = username,
                    studentName = "用户",
                    loginMode = mode,
                    sessionStatus = AuthSessionStatus.AUTHENTICATED,
                )
                Result.success(Unit)
            }
            is LoginResult.Failure -> {
                state.value = AuthState(
                    isLoggedIn = false,
                    studentId = "",
                    studentName = "",
                    sessionStatus = AuthSessionStatus.UNAUTHENTICATED,
                )
                Result.failure(IllegalStateException(result.message))
            }
        }
    }

    override suspend fun autoLogin(): Boolean {
        autoLoginResult()
        return state.value.sessionStatus.canAccessRemoteData
    }

    override suspend fun autoLoginResult(): AuthAutoLoginResult {
        state.value = state.value.copy(
            isLoggedIn = false,
            sessionStatus = AuthSessionStatus.RESTORING,
            sessionResolved = false,
        )
        val autoLoginResult = when (currentLoginMode()) {
            LoginMode.PORTAL -> portalSession.autoLogin().toAuthAutoLoginResult()
            LoginMode.JWXT_DIRECT -> directSession.autoLogin().toAuthAutoLoginResult()
            null -> AuthAutoLoginResult.MissingCredentials
        }
        val status = autoLoginResult.toSessionStatus()
        state.value = AuthState(
            isLoggedIn = status == AuthSessionStatus.AUTHENTICATED,
            studentId = if (status == AuthSessionStatus.AUTHENTICATED) "已登录" else "",
            studentName = if (status == AuthSessionStatus.AUTHENTICATED) "用户" else "",
            loginMode = currentLoginMode(),
            sessionStatus = status,
            sessionResolved = true,
        )
        return autoLoginResult
    }

    override suspend fun logout() {
        portalSession.logout()
        directSession.logout()
        webCookieCleaner.clear()
        state.value = AuthState(
            isLoggedIn = false,
            studentId = "",
            studentName = "",
            sessionStatus = AuthSessionStatus.UNAUTHENTICATED,
        )
    }

    override suspend fun preparePortalWebSession(
        forceRefresh: Boolean,
    ): PortalWebSessionResult = portalSession.castgc(forceRefresh)

    private suspend fun currentLoginMode(): LoginMode? =
        credentialsProvider.loadCredentials()?.loginMode
            ?: sessionFlagStore.getLoginMode()
}

internal fun AutoLoginResult.toAuthAutoLoginResult(): AuthAutoLoginResult = when (this) {
    AutoLoginResult.SessionValid,
    AutoLoginResult.ReloggedIn,
    -> AuthAutoLoginResult.Success
    AutoLoginResult.MissingCredentials -> AuthAutoLoginResult.MissingCredentials
    is AutoLoginResult.CredentialsRejected ->
        AuthAutoLoginResult.CredentialsRejected(reason.toSyncFailureReason(), message)
    is AutoLoginResult.NetworkFailure ->
        AuthAutoLoginResult.NetworkFailure(reason.toSyncFailureReason(), message)
}

internal fun LoginFailureReason.toSyncFailureReason(): SyncFailureReason =
    when (this) {
        LoginFailureReason.NetworkTimeout -> SyncFailureReason.NetworkTimeout
        LoginFailureReason.NetworkUnavailable -> SyncFailureReason.NetworkUnavailable
        LoginFailureReason.InvalidCredentials -> SyncFailureReason.CredentialsRejected
        LoginFailureReason.CaptchaRequired -> SyncFailureReason.CaptchaRequired
        LoginFailureReason.ServerChanged -> SyncFailureReason.ParseError
        LoginFailureReason.Unknown -> SyncFailureReason.Unknown
    }

internal fun AutoLoginResult.toSyncFailure(): SyncFailureReason? =
    when (this) {
        AutoLoginResult.SessionValid,
        AutoLoginResult.ReloggedIn,
        -> null
        AutoLoginResult.MissingCredentials -> SyncFailureReason.AuthRequired
        is AutoLoginResult.CredentialsRejected -> reason.toSyncFailureReason()
        is AutoLoginResult.NetworkFailure -> reason.toSyncFailureReason()
    }

internal fun AuthAutoLoginResult.toSessionStatus(): AuthSessionStatus = when (this) {
    AuthAutoLoginResult.Success -> AuthSessionStatus.AUTHENTICATED
    is AuthAutoLoginResult.NetworkFailure -> AuthSessionStatus.OFFLINE_AUTHENTICATED
    AuthAutoLoginResult.MissingCredentials,
    is AuthAutoLoginResult.CredentialsRejected,
    -> AuthSessionStatus.UNAUTHENTICATED
}
