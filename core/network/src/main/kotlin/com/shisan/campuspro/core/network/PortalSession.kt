package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.Credentials
import com.shisan.campuspro.core.model.LoginMode
import com.shisan.campuspro.core.model.PortalWebSessionResult
import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 门户 CAS 统一认证会话。
 *
 * 负责门户登录模式（[LoginMode.PORTAL]）的完整认证链：
 * CAS 登录 → OAuth2 桥接到教务系统，并对外提供 CASTGC 供
 * 可信电子凭证等下游系统建立独立 SSO 会话。
 *
 * JWXT 直连登录是另一套独立系统，由 [JwxtDirectSession] 承担。
 *
 * ## 并发保证
 * 所有会修改会话状态的方法（login/autoLogin/logout/castgc）通过内部 [Mutex] 串行化。
 *
 * ## Cookie 存储
 * 与 JwxtDirectSession / CredentialClient 共享 [JwxtClient.sharedCookieStorage]。
 * CAS 登录后 CASTGC 写入该存储，OAuth 桥接和 CredentialClient 复用同一会话。
 */
class PortalSession(
    private val client: JwxtClient,
    private val credentialsProvider: CredentialsProvider,
    private val sessionFlagStore: SessionFlagStore,
    private val portalCasSession: PortalCasSession = PortalCasSession(),
    private val portalLogin: suspend (String, String) -> Result<PortalLoginResponse> =
        portalCasSession::login,
) {
    private val authMutex = Mutex()
    private var lastProbeSuccess = false
    private var lastProbeTimeMs = 0L

    suspend fun login(
        username: String,
        password: String,
        rememberCredentials: Boolean = false,
    ): LoginResult = authMutex.withLock {
        loginPortalLocked(username, password, rememberCredentials)
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
        // 只有门户凭据才由本会话处理；直连凭据归 JwxtDirectSession
        if (credentials.loginMode != LoginMode.PORTAL) {
            return AutoLoginResult.MissingCredentials
        }
        when (val result = loginPortalLocked(
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
        // 同步清理 CAS 认证客户端的独立 Cookie，避免下次门户登录时残留会话干扰
        HttpClientFactory.casAuthCookieStorage.reset()
        sessionFlagStore.setHasSession(false)
        sessionFlagStore.setLoginMode(null)
        credentialsProvider.clearCredentials()
    }

    /**
     * 获取门户统一认证会话 Cookie（CASTGC），供可信电子凭证等系统建立独立 SSO 会话。
     *
     * 无参版本优先返回缓存 CASTGC；[forceRefresh] 为 true 时强制用已存凭据重走完整 CAS 登录。
     */
    suspend fun castgc(forceRefresh: Boolean = false): PortalWebSessionResult = authMutex.withLock {
        if (currentLoginMode() != LoginMode.PORTAL) {
            return@withLock PortalWebSessionResult.PortalLoginRequired
        }

        if (!forceRefresh) {
            portalCastgc()?.let { return@withLock PortalWebSessionResult.Ready(it) }
        }

        val credentials = credentialsProvider.loadCredentials()
            ?: return@withLock PortalWebSessionResult.PortalLoginRequired

        when (val loginResult = loginPortalLocked(
            credentials.username,
            credentials.password,
            rememberCredentials = true,
        )) {
            LoginResult.Success -> portalCastgc()
                ?.let(PortalWebSessionResult::Ready)
                ?: PortalWebSessionResult.Failure("门户登录成功，但未取得统一认证会话")
            is LoginResult.Failure -> PortalWebSessionResult.Failure(loginResult.message)
        }
    }

    /** 当前登录模式：仅当凭据或持久化标志表明是门户模式时返回 PORTAL。 */
    private suspend fun currentLoginMode(): LoginMode? =
        credentialsProvider.loadCredentials()?.loginMode
            ?: sessionFlagStore.getLoginMode()

    suspend fun getCoursePage(term: String, week: String = ""): String =
        client.get("/jsxsd/xskb/xskb_list.do?xnxq01id=$term&zc=$week&demo=").text

    suspend fun getGradesPage(): String =
        client.post("/jsxsd/kscj/cjcx_list", mapOf("kksj" to "", "kcxz" to "", "kcmc" to "", "xsfs" to "max")).text

    suspend fun getExamPage(termId: String): String =
        client.post("/jsxsd/xsks/xsksap_list", mapOf("xqlbmc" to "", "xnxqid" to termId, "xqlb" to "")).text

    private suspend fun loginPortalLocked(
        username: String,
        password: String,
        rememberCredentials: Boolean,
    ): LoginResult {
        // 重置 Cookie 存储，避免旧会话干扰本次登录
        JwxtClient.sharedCookieStorage.reset()
        clearProbeCache()
        return portalLogin(username, password).fold(
            onSuccess = { response ->
                lastProbeTimeMs = 0L
                // 验证共享 Cookie 存储中已有有效 JWXT 会话 Cookie（JSESSIONID）
                if (!probeSession()) {
                    LoginResult.Failure("门户登录成功但无法建立教务系统会话，请稍后重试")
                } else {
                    sessionFlagStore.setHasSession(true)
                    sessionFlagStore.setLoginMode(LoginMode.PORTAL)
                    persistCredentials(username, password, rememberCredentials)
                    LoginResult.Success
                }
            },
            onFailure = { error ->
                LoginResult.Failure("门户认证失败：${error.message ?: "网络连接失败"}")
            },
        )
    }

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
            credentialsProvider.saveCredentials(Credentials(username, password, LoginMode.PORTAL))
        } else {
            credentialsProvider.clearCredentials()
        }
    }

    private suspend fun portalCastgc(): String? =
        JwxtClient.sharedCookieStorage
            .get(Url(PortalCookieUrl))
            .firstOrNull { it.name == "CASTGC" }
            ?.value
}

private const val PortalCookieUrl = "https://authserver.jsu.edu.cn/authserver/login"
