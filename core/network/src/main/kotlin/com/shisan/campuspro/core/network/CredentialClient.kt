package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.CredentialEmail
import com.shisan.campuspro.core.model.CredentialEmailDraft
import com.shisan.campuspro.core.model.CredentialFailure
import com.shisan.campuspro.core.model.CredentialFailureKind
import com.shisan.campuspro.core.model.CredentialOrder
import com.shisan.campuspro.core.model.CredentialOrderPage
import com.shisan.campuspro.core.model.CredentialService
import com.shisan.campuspro.core.model.CredentialTemplate
import com.shisan.campuspro.core.model.PortalWebSessionResult
import com.shisan.campuspro.core.model.credentialOrderProgress
import com.shisan.campuspro.core.model.credentialPaymentStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

object CredentialEndpoints {
    const val ServiceOrigin = "https://kxdzpz.jsu.edu.cn"
    const val WebVpnUrl =
        "https://webvpn.jsu.edu.cn/https/" +
            "77726476706e6973746865626573747421bfef4586372a265a6d1dc7a99c406d36be/"
    const val OAuthClientId = "YOUR_CREDENTIAL_OAUTH_CLIENT_ID"
    val OAuthUrl: String
        get() = "https://authserver.jsu.edu.cn/authserver/oauth2.0/authorize" +
            "?client_id=$OAuthClientId&response_type=code" +
            "&redirect_uri=${ServiceOrigin.encodeURLParameter()}"
}

class CredentialApiException(message: String) : IllegalStateException(message)

object CredentialResponseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val supportedServices = setOf("成绩单", "在读证明")

    fun dashboard(body: String): List<CredentialService> {
        val result = body.result()
        return result.obj("service_list").records().map { item ->
            val name = item.string("name")
            CredentialService(
                id = item.string("id"),
                name = name,
                nameEn = item.string("nameEn"),
                enabled = item.string("status") == "1",
                feeEnabled = item.string("feesStatus") == "1",
                nativeSupported = name in supportedServices,
            )
        }
    }

    fun templates(body: String): List<CredentialTemplate> = body.result().records().map { item ->
        CredentialTemplate(
            id = item.string("id"),
            serviceId = item.string("serviceId"),
            displayName = item.string("displayName").ifBlank { item.string("name") },
            price = item.double("avgPrice"),
            type = item.string("type"),
            courseNature = item.string("kcxz"),
            scoreBand = item.string("xfjd"),
            selectedCourses = item.string("xzkc"),
        )
    }

    fun emails(body: String): List<CredentialEmail> = body.result().records().map { item ->
        CredentialEmail(
            id = item.string("id"),
            email = item.string("email"),
            ccEmail = item.string("ccEmail"),
            title = item.string("title"),
            content = item.string("content"),
            isDefault = item.boolean("isdefault"),
        )
    }

    fun orders(body: String): CredentialOrderPage {
        val result = body.result()
        val items = result.records().map { item ->
            val price = item.double("price")
            CredentialOrder(
                id = item.string("id"),
                orderNumber = item.string("orderno"),
                serviceName = item.string("serviceName").ifBlank { item.string("name") },
                email = item.string("email"),
                price = price,
                progress = credentialOrderProgress(
                    item.string("applyStatus").ifBlank { item.string("issend") },
                ),
                paymentStatus = credentialPaymentStatus(item.string("payStatus"), price),
                appliedAt = item.string("applyTime").ifBlank { item.string("createTime") },
            )
        }
        return CredentialOrderPage(
            items = items,
            page = result.int("current", 1),
            pageSize = result.int("size", 10),
            total = result.int("total", items.size),
        )
    }

    fun token(body: String): String = body.result()
        .string("token")
        .ifBlank { throw CredentialApiException("统一认证未返回访问令牌") }
    fun createdOrder(body: String): CredentialOrder {
        val result = body.result()
        val price = result.double("price")
        return CredentialOrder(
            id = result.string("id"),
            orderNumber = result.string("orderno"),
            serviceName = "",
            email = "",
            price = price,
            progress = credentialOrderProgress(result.string("applyStatus")),
            paymentStatus = credentialPaymentStatus(result.string("payStatus"), price),
            appliedAt = result.string("applyTime"),
        )
    }

    fun previewPath(body: String): String = when (val result = body.resultElement()) {
        is JsonPrimitive -> result.contentOrNull.orEmpty()
        is JsonObject -> result.string("filePath")
            .ifBlank { result.string("url") }
            .ifBlank { result.string("path") }
            .ifBlank { result.string("file") }
        else -> ""
    }.ifBlank { throw CredentialApiException("学校未返回预览文件") }

    fun validate(body: String) { body.resultElement() }

    private fun String.root(): JsonObject = runCatching { json.parseToJsonElement(this).jsonObject }
        .getOrElse { throw CredentialApiException("学校凭证服务响应格式已变化") }

    private fun String.resultElement(): JsonElement {
        val root = root()
        val success = root["success"]?.jsonPrimitive?.booleanOrNull
            ?: (root["code"]?.jsonPrimitive?.intOrNull in setOf(0, 200))
        if (!success) throw CredentialApiException(root.string("message").ifBlank { "学校凭证服务请求失败" })
        return root["result"] ?: JsonObject(emptyMap())
    }
    private fun String.result(): JsonObject =
        resultElement() as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.obj(name: String): JsonObject =
        this[name] as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.records(): List<JsonObject> =
        (this["records"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.double(name: String): Double =
        this[name]?.jsonPrimitive?.doubleOrNull ?: 0.0
    private fun JsonObject.int(name: String, default: Int): Int =
        this[name]?.jsonPrimitive?.intOrNull ?: default
    private fun JsonObject.boolean(name: String): Boolean =
        this[name]?.jsonPrimitive?.booleanOrNull ?: string(name) == "1"
}

class CredentialClient(
    private val portalSessionProvider: suspend (forceRefresh: Boolean) -> PortalWebSessionResult,
    private val client: HttpClient = HttpClientFactory.credential(),
) {
    private val authMutex = Mutex()
    private var token: String? = null

    suspend fun services(): List<CredentialService> =
        authorizedGet("/student/dashboardZufe/index") {
            CredentialResponseParser.dashboard(it)
        }
    suspend fun templates(serviceId: String): List<CredentialTemplate> = authorizedGet(
        "/student/caTemplate/list",
        mapOf("code" to serviceId),
    ) {
        CredentialResponseParser.templates(it)
    }
    suspend fun emails(): List<CredentialEmail> =
        authorizedGet(
            "/student/caUserEmail/list",
            mapOf("pageNo" to "1", "pageSize" to "1000"),
        ) {
            CredentialResponseParser.emails(it)
        }
    suspend fun orders(page: Int, pageSize: Int): CredentialOrderPage = authorizedGet(
        "/student/caOrder/list",
        mapOf(
            "pageNo" to page.toString(),
            "pageSize" to pageSize.toString(),
            "sort" to "orderno",
            "order" to "desc",
        ),
    ) { CredentialResponseParser.orders(it) }

    suspend fun addEmail(draft: CredentialEmailDraft) = writeEmail(
        "/student/caUserEmail/add",
        draft,
    )
    suspend fun editEmail(id: String, draft: CredentialEmailDraft) = writeEmail(
        "/student/caUserEmail/edit",
        draft,
        id,
    )
    suspend fun deleteEmail(id: String) {
        authorizedPost("/student/caUserEmail/delete", query = mapOf("id" to id), body = null)
    }

    suspend fun preview(template: CredentialTemplate): ByteArray {
        val path = authorizedGet(
            "/student/caTemplate/preview_file",
            mapOf(
                "templateId" to template.id,
                "isbzf" to "0",
                "kcxz" to template.courseNature,
                "xfjd" to template.scoreBand,
                "xzkc" to template.selectedCourses,
            ),
        ) { CredentialResponseParser.previewPath(it) }
        val previewUrl = when {
            path.startsWith("http") || path.startsWith("/") -> path
            else -> "/sys/common/view/$path"
        }
        return authorizedBytes(previewUrl)
    }

    suspend fun createFreeOrder(template: CredentialTemplate, emailId: String): CredentialOrder {
        require(template.isFree) { "收费凭证必须前往学校网页办理" }
        val body = jsonBody(
            "price" to template.price,
            "templateId" to template.id,
            "emailId" to emailId,
            "bzf" to "0",
            "kcxz" to template.courseNature,
            "xfjd" to template.scoreBand,
            "xzkc" to template.selectedCourses,
            "type" to template.type,
        )
        return try {
            CredentialResponseParser.createdOrder(
                authorizedPost("/student/caOrder/add", body = body),
            )
        } catch (failure: CredentialFailure) {
            if (failure.kind == CredentialFailureKind.NetworkUnavailable) {
                throw CredentialFailure(
                    CredentialFailureKind.SubmissionUncertain,
                    "网络中断，订单提交结果未知",
                    failure,
                )
            }
            throw failure
        }
    }

    fun clearSession() {
        token = null
    }

    private suspend fun writeEmail(path: String, draft: CredentialEmailDraft, id: String? = null) {
        val errors = draft.validationErrors()
        require(errors.isEmpty()) { errors.first() }
        val fields = mutableListOf<Pair<String, Any?>>(
            "email" to draft.email.trim(),
            "title" to draft.title,
            "content" to draft.content,
            "ccEmail" to draft.ccEmail.trim(),
            "isdefault" to draft.isDefault,
        )
        if (id != null) fields += "id" to id
        authorizedPost(path, body = jsonBody(*fields.toTypedArray()))
    }

    private suspend fun <T> authorizedGet(
        path: String,
        query: Map<String, String> = emptyMap(),
        parse: (String) -> T,
    ): T = withSingleReauthentication { accessToken ->
        val response = client.get(absoluteUrl(path)) {
            header("X-Access-Token", accessToken)
            query.forEach { (key, value) -> parameter(key, value) }
        }
        validateStatus(response)
        parse(response.bodyAsText())
    }

    private suspend fun authorizedBytes(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): ByteArray = withSingleReauthentication { accessToken ->
        val response = client.get(absoluteUrl(path)) {
            header("X-Access-Token", accessToken)
            query.forEach { (key, value) -> parameter(key, value) }
        }
        validateStatus(response)
        response.bodyAsBytes()
    }

    private suspend fun authorizedPost(
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String?,
    ): String = withSingleReauthentication { accessToken ->
        val response = client.post(CredentialEndpoints.ServiceOrigin + path) {
            header("X-Access-Token", accessToken)
            query.forEach { (key, value) -> parameter(key, value) }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        validateStatus(response)
        response.bodyAsText().also(CredentialResponseParser::validate)
    }

    private suspend fun <T> withSingleReauthentication(block: suspend (String) -> T): T {
        return try {
            block(ensureToken())
        } catch (first: Throwable) {
            if (!first.isCredentialAuthenticationFailure()) {
                throw first.toCredentialFailure()
            }
            token = null
            try {
                block(ensureToken())
            } catch (second: Throwable) {
                throw second.toCredentialFailure()
            }
        }
    }

    private suspend fun ensureToken(): String = token ?: authMutex.withLock {
        token ?: authenticate().also { token = it }
    }

    private suspend fun authenticate(): String {
        val castgc = portalSessionProvider(false).requireCastgc()
        return authenticateWithCookie(castgc, allowPortalRefresh = true)
    }

    private suspend fun authenticateWithCookie(
        castgc: String,
        allowPortalRefresh: Boolean,
    ): String {
        val authorize = client.get(CredentialEndpoints.OAuthUrl) { cookie("CASTGC", castgc) }
        val callbackUrl = authorize.oauthCallbackUrl()
        val code = callbackUrl.parameters["code"]
        if (code == null) {
            val oauthError = callbackUrl.parameters["error"].orEmpty()
            if (oauthError == "access_denied" || oauthError == "unauthorized_client") {
                throw CredentialFailure(
                    CredentialFailureKind.Authentication,
                    "学校未授予可信电子凭证访问权限",
                )
            }
            if (callbackUrl.isPortalLoginUrl()) {
                if (!allowPortalRefresh) {
                    throw CredentialFailure(
                        CredentialFailureKind.PortalLoginRequired,
                        "门户会话已失效，请重新使用门户登录",
                    )
                }
                val refreshedCastgc = portalSessionProvider(true).requireCastgc(
                    refreshAttempt = true,
                )
                return authenticateWithCookie(
                    castgc = refreshedCastgc,
                    allowPortalRefresh = false,
                )
            }
            throw CredentialApiException("学校统一认证回调格式已变化")
        }
        val response = client.get(
            CredentialEndpoints.ServiceOrigin + "/cas/clientJsdx/validateLogin",
        ) {
            parameter("code", code)
            parameter("state", CredentialEndpoints.ServiceOrigin)
        }
        return CredentialResponseParser.token(response.bodyAsText())
    }

    private fun HttpResponse.oauthCallbackUrl(): Url =
        headers[HttpHeaders.Location]?.let(::Url) ?: call.request.url

    private fun validateStatus(response: HttpResponse) {
        when {
            response.status == HttpStatusCode.Unauthorized ||
                response.status == HttpStatusCode.Forbidden -> throw CredentialFailure(
                CredentialFailureKind.Authentication,
                "可信电子凭证登录已过期",
            )
            response.status.value >= 500 -> throw CredentialFailure(
                CredentialFailureKind.ServiceMaintenance,
                "学校可信电子凭证服务维护中",
            )
        }
    }

    private fun absoluteUrl(path: String): String =
        if (path.startsWith("http")) path else CredentialEndpoints.ServiceOrigin + path

    private fun jsonBody(vararg fields: Pair<String, Any?>): String = JsonObject(
        fields.associate { (key, value) ->
            key to when (value) {
                is Boolean -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                else -> JsonPrimitive(value?.toString().orEmpty())
            }
        },
    ).toString()

}

private fun PortalWebSessionResult.requireCastgc(
    refreshAttempt: Boolean = false,
): String = when (this) {
    is PortalWebSessionResult.Ready -> castgc
    PortalWebSessionResult.PortalLoginRequired -> throw CredentialFailure(
        CredentialFailureKind.PortalLoginRequired,
        "请先使用门户统一认证登录",
    )
    is PortalWebSessionResult.Failure -> {
        if (refreshAttempt) {
            throw CredentialFailure(
                CredentialFailureKind.PortalLoginRequired,
                "门户会话已失效，请重新使用门户登录",
            )
        }
        throw CredentialFailure(CredentialFailureKind.Unknown, message)
    }
}

private fun Url.isPortalLoginUrl(): Boolean =
    host.equals("authserver.jsu.edu.cn", ignoreCase = true) &&
        encodedPath.contains("/authserver/login")

private fun Throwable.isCredentialAuthenticationFailure(): Boolean =
    (this as? CredentialFailure)?.kind == CredentialFailureKind.Authentication ||
        (this is CredentialApiException && message.orEmpty().let { message ->
            message.contains("登录超时") ||
                message.contains("未登录") ||
                message.contains("权限")
        })

private fun Throwable.toCredentialFailure(): Throwable = when (this) {
    is CredentialFailure -> this
    is IOException -> CredentialFailure(
        CredentialFailureKind.NetworkUnavailable,
        "无法连接学校可信电子凭证服务",
        this,
    )
    else -> this
}

