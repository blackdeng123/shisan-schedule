package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.Credentials
import com.shisan.campuspro.core.model.LoginMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * JWXT 教务系统直连会话。
 *
 * 仅负责直连登录模式（[LoginMode.JWXT_DIRECT]）的会话生命周期和页面抓取。
 * 门户 CAS 统一认证是另一套独立系统，由 [PortalSession] 承担。
 *
 * ## 并发保证
 * 所有会修改会话状态的方法（login/autoLogin/logout）通过内部 [Mutex] 串行化，
 * 防止多个协程同时登录导致 Cookie 紊乱。
 *
 * ## Cookie 存储
 * 与 PortalSession / CredentialClient 共享 [JwxtClient.sharedCookieStorage]。
 * login/logout 时会重置它，避免旧会话干扰。
 */
class JwxtDirectSession(
    private val client: JwxtClient,
    private val credentialsProvider: CredentialsProvider,
    private val sessionFlagStore: SessionFlagStore,
) {
    private val authMutex = Mutex()
    private var lastProbeSuccess = false
    private var lastProbeTimeMs = 0L

    suspend fun login(
        username: String,
        password: String,
        rememberCredentials: Boolean = false,
    ): LoginResult = authMutex.withLock {
        loginLocked(username, password, rememberCredentials)
    }

    /** 无锁登录实现，供 [login] 与 [autoLogin]（已持锁）复用，避免 Mutex 自死锁。 */
    private suspend fun loginLocked(
        username: String,
        password: String,
        rememberCredentials: Boolean,
    ): LoginResult {
        // 重置 Cookie 存储，避免旧会话干扰本次登录
        JwxtClient.sharedCookieStorage.reset()
        clearProbeCache()
        val encoded = "${JwxtEncoding.encodeInp(username)}%%%${JwxtEncoding.encodeInp(password)}"
        return runCatching {
            val response = client.post(
                "/jsxsd/xk/LoginToXk",
                mapOf(
                    "encoded" to encoded,
                    "userAccount" to username,
                    "userPassword" to password,
                ),
            )
            val error = JwxtLoginDetector.errorMessage(response.text)
            if (error != null) {
                LoginResult.Failure(error, reason = error.toLoginFailureReason())
            } else if (JwxtLoginDetector.isSuccess(response.text, response.url) || probeSession()) {
                sessionFlagStore.setHasSession(true)
                sessionFlagStore.setLoginMode(LoginMode.JWXT_DIRECT)
                persistCredentials(username, password, rememberCredentials)
                LoginResult.Success
            } else {
                LoginResult.Failure("登录失败，请检查学号和密码", LoginFailureReason.InvalidCredentials)
            }
        }.getOrElse { it.toLoginFailure() }
    }

    suspend fun autoLogin(forceRefresh: Boolean = false): AutoLoginResult = authMutex.withLock {
        if (forceRefresh) {
            clearProbeCache()
        }
        if (sessionFlagStore.hasSession() && probeSession()) {
            return AutoLoginResult.SessionValid
        }
        sessionFlagStore.setHasSession(false)
        val credentials = credentialsProvider.loadCredentials()
            ?: return AutoLoginResult.MissingCredentials
        // 只有直连凭据才由本会话处理；门户凭据归 PortalSession
        if (credentials.loginMode != LoginMode.JWXT_DIRECT) {
            return AutoLoginResult.MissingCredentials
        }
        when (val result = loginLocked(
            credentials.username,
            credentials.password,
            rememberCredentials = true,
        )) {
            LoginResult.Success -> {
                clearProbeCache()
                AutoLoginResult.ReloggedIn
            }
            is LoginResult.Failure -> result.toAutoLoginResult()
        }
    }

    suspend fun logout() = authMutex.withLock {
        clearProbeCache()
        JwxtClient.sharedCookieStorage.reset()
        sessionFlagStore.setHasSession(false)
        sessionFlagStore.setLoginMode(null)
        credentialsProvider.clearCredentials()
    }

    suspend fun getCoursePage(term: String, week: String = ""): String =
        client.get("/jsxsd/xskb/xskb_list.do?xnxq01id=$term&zc=$week&demo=").text

    suspend fun getGradesPage(): String =
        client.post("/jsxsd/kscj/cjcx_list", mapOf("kksj" to "", "kcxz" to "", "kcmc" to "", "xsfs" to "max")).text

    suspend fun getExamPage(termId: String): String =
        client.post("/jsxsd/xsks/xsksap_list", mapOf("xqlbmc" to "", "xnxqid" to termId, "xqlb" to "")).text

    private suspend fun probeSession(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastProbeTimeMs < 300_000L) return lastProbeSuccess  // 5 分钟内走缓存
        val result = runCatching {
            val response = client.get("/jsxsd/xskb/xskb_list.do")
            response.ok && (
                response.text.contains("kbtable", ignoreCase = true) ||
                    JwxtLoginDetector.isSuccess(response.text, response.url)
                )
        }.getOrDefault(false)
        lastProbeSuccess = result
        lastProbeTimeMs = now
        return result
    }

    private fun clearProbeCache() {
        lastProbeSuccess = false
        lastProbeTimeMs = 0L
    }

    private suspend fun persistCredentials(
        username: String,
        password: String,
        rememberCredentials: Boolean,
    ) {
        if (rememberCredentials) {
            credentialsProvider.saveCredentials(Credentials(username, password, LoginMode.JWXT_DIRECT))
        } else {
            credentialsProvider.clearCredentials()
        }
    }
}

private fun String.toLoginFailureReason(): LoginFailureReason = when {
    contains("验证码") -> LoginFailureReason.CaptchaRequired
    contains("用户名或密码错误") || contains("学号和密码") -> LoginFailureReason.InvalidCredentials
    contains("缺少") -> LoginFailureReason.ServerChanged
    else -> LoginFailureReason.Unknown
}
