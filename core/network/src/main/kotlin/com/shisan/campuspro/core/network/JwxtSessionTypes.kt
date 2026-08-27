package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.Credentials
import com.shisan.campuspro.core.model.LoginMode
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.SocketTimeoutException

/**
 * 认证会话共享类型。
 *
 * 由 [JwxtDirectSession]（直连教务系统）和 [PortalSession]（门户 CAS 统一认证）共用。
 * 这两个会话是独立系统，但共享同一套结果类型、错误分类和持久化接口。
 */

enum class LoginFailureReason {
    NetworkTimeout,
    NetworkUnavailable,
    InvalidCredentials,
    CaptchaRequired,
    ServerChanged,
    Unknown,
}

sealed interface LoginResult {
    data object Success : LoginResult
    data class Failure(
        val message: String,
        val reason: LoginFailureReason = LoginFailureReason.Unknown,
    ) : LoginResult {
        val needCaptcha: Boolean
            get() = reason == LoginFailureReason.CaptchaRequired
    }
}

sealed interface AutoLoginResult {
    data object SessionValid : AutoLoginResult
    data object ReloggedIn : AutoLoginResult
    data object MissingCredentials : AutoLoginResult
    data class CredentialsRejected(
        val reason: LoginFailureReason,
        val message: String,
    ) : AutoLoginResult
    data class NetworkFailure(
        val reason: LoginFailureReason,
        val message: String,
    ) : AutoLoginResult
}

fun AutoLoginResult.keepsLocalLogin(): Boolean =
    when (this) {
        AutoLoginResult.SessionValid,
        AutoLoginResult.ReloggedIn,
        is AutoLoginResult.NetworkFailure,
        -> true
        AutoLoginResult.MissingCredentials,
        is AutoLoginResult.CredentialsRejected,
        -> false
    }

interface CredentialsProvider {
    suspend fun loadCredentials(): Credentials?
    suspend fun saveCredentials(credentials: Credentials)
    suspend fun clearCredentials()
}

interface SessionFlagStore {
    suspend fun hasSession(): Boolean
    suspend fun setHasSession(value: Boolean)
    suspend fun getLoginMode(): LoginMode?
    suspend fun setLoginMode(mode: LoginMode?)
}

fun LoginFailureReason.defaultMessage(): String = when (this) {
    LoginFailureReason.NetworkTimeout -> "网络连接超时，请稍后重试"
    LoginFailureReason.NetworkUnavailable -> "网络连接失败，请稍后重试"
    LoginFailureReason.InvalidCredentials -> "账号或密码可能已变更，请重新登录"
    LoginFailureReason.CaptchaRequired -> "需要验证码，请在电脑端登录后再试"
    LoginFailureReason.ServerChanged -> "教务系统页面结构变化，暂时无法登录"
    LoginFailureReason.Unknown -> "登录失败，请稍后重试"
}

/** 将网络层异常分类为登录失败原因。两个会话共用。 */
internal fun Throwable.toLoginFailure(): LoginResult.Failure {
    val reason = when (this) {
        is HttpRequestTimeoutException,
        is SocketTimeoutException,
        -> LoginFailureReason.NetworkTimeout
        else -> LoginFailureReason.NetworkUnavailable
    }
    return LoginResult.Failure(reason.defaultMessage(), reason)
}

/** 将登录失败分类为自动登录结果。两个会话共用。 */
internal fun LoginResult.Failure.toAutoLoginResult(): AutoLoginResult =
    when (reason) {
        LoginFailureReason.NetworkTimeout,
        LoginFailureReason.NetworkUnavailable,
        -> AutoLoginResult.NetworkFailure(reason, reason.defaultMessage())
        LoginFailureReason.InvalidCredentials,
        LoginFailureReason.CaptchaRequired,
        LoginFailureReason.ServerChanged,
        LoginFailureReason.Unknown,
        -> AutoLoginResult.CredentialsRejected(reason, message)
    }
