package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.common.CAMPUS_DESKTOP_USER_AGENT
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking

/**
 * HTTP 客户端工厂。
 *
 * 统一管理项目中 4 种预配置 HttpClient 的创建逻辑，取代原先散落在
 * JwxtClient / PortalCasSession / CredentialClient 中的私有构造函数。
 *
 * 每个方法对应一种业务场景的推荐配置：
 * - [jwxt]：教务系统页面抓取（共享 Cookie + 重试 + 桌面 UA）
 * - [casAuth]：门户 CAS 认证（独立 Cookie + 更长超时 + 不重试——认证失败重试无意义）
 * - [jwxtOAuth]：门户 → 教务 OAuth 桥接（共享 Cookie + 预填 CASTGC + 重试）
 * - [credential]：可信电子凭证 JSON API（独立 Cookie + 跟随重定向 + JSON Accept）
 */
object HttpClientFactory {

    const val JWXT_BASE_URL = "https://jwxt.jsu.edu.cn"

    /**
     * CAS 认证客户端的独立 Cookie 存储。
     * 每次 CAS 登录前重置，防止退出登录后残留的 authserver 会话 Cookie
     * 让登录页返回缺少 execution 的页面。
     */
    val casAuthCookieStorage = ResettableCookieStorage()

    private const val CASTGC_DOMAIN = "authserver.jsu.edu.cn"
    private const val CASTGC_PATH = "/authserver"
    private const val DEFAULT_UA = CAMPUS_DESKTOP_USER_AGENT

    private val htmlAcceptHeader =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"

    /** 教务系统页面抓取客户端。JwxtClient 默认使用。 */
    fun jwxt(baseUrl: String = JWXT_BASE_URL): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(maxRetries = 3)
            exponentialDelay()
        }
        install(HttpCookies) {
            storage = JwxtClient.sharedCookieStorage
        }
        defaultRequest {
            header("User-Agent", DEFAULT_UA)
            header("Accept", htmlAcceptHeader)
            header("Accept-Language", "zh-CN,zh;q=0.9")
            header("Referer", "$baseUrl/jsxsd/")
        }
        engine {
            config {
                retryOnConnectionFailure(true)
            }
        }
    }

    /** 门户 CAS 认证客户端。PortalCasSession 登录流程使用。 */
    fun casAuth(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }
        install(HttpCookies) {
            storage = casAuthCookieStorage
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, DEFAULT_UA)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9,en;q=0.8")
        }
    }

    /** 门户 → 教务 OAuth 桥接客户端。将 CASTGC 预填到共享 Cookie 存储后连接教务系统。 */
    fun jwxtOAuth(castgc: String): HttpClient {
        val cookieStorage = JwxtClient.sharedCookieStorage
        runBlocking {
            cookieStorage.addCookie(
                requestUrl = Url("https://authserver.jsu.edu.cn/authserver"),
                cookie = Cookie(
                    name = "CASTGC",
                    value = castgc,
                    domain = CASTGC_DOMAIN,
                    path = CASTGC_PATH,
                    secure = true,
                    httpOnly = true,
                ),
            )
        }
        return HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(maxRetries = 3)
                exponentialDelay()
            }
            install(HttpCookies) {
                storage = cookieStorage
            }
            defaultRequest {
                header(HttpHeaders.UserAgent, DEFAULT_UA)
                header(HttpHeaders.Accept, htmlAcceptHeader)
                header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9")
                header(HttpHeaders.Referrer, "https://jwxt.jsu.edu.cn/jsxsd/")
            }
            engine {
                config {
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    /** 可信电子凭证 JSON API 客户端。CredentialClient 使用。 */
    fun credential(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = true
        install(HttpCookies)
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, DEFAULT_UA)
            header(HttpHeaders.Accept, "application/json, text/plain, */*")
        }
    }
}
