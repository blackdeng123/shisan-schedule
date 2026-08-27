package com.shisan.campuspro.core.model

data class CredentialService(
    val id: String,
    val name: String,
    val nameEn: String = "",
    val enabled: Boolean = true,
    val feeEnabled: Boolean = false,
    val nativeSupported: Boolean = false,
)

data class CredentialTemplate(
    val id: String,
    val serviceId: String,
    val displayName: String,
    val price: Double,
    val type: String,
    val courseNature: String = "",
    val scoreBand: String = "",
    val selectedCourses: String = "",
) {
    val isFree: Boolean get() = price == 0.0
}

data class CredentialEmail(
    val id: String,
    val email: String,
    val ccEmail: String = "",
    val title: String,
    val content: String,
    val isDefault: Boolean = false,
)

data class CredentialEmailDraft(
    val email: String,
    val ccEmail: String,
    val title: String,
    val content: String,
    val isDefault: Boolean = false,
) {
    fun validationErrors(): List<String> = buildList {
        if (!email.trim().isCredentialEmail()) add("请输入正确的收件邮箱")
        val invalidCc = ccEmail
            .split(';')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .any { !it.isCredentialEmail() }
        if (invalidCc) add("请输入正确的抄送邮箱，多个地址用分号分隔")
        if (title.isBlank()) add("请输入邮件标题")
        if (title.length > 60) add("邮件标题不能超过 60 字")
        if (content.isBlank()) add("请输入邮件内容")
        if (content.length > 1000) add("邮件内容不能超过 1000 字")
    }
}

sealed interface CredentialOrderProgress {
    data object Pending : CredentialOrderProgress
    data object Sent : CredentialOrderProgress
    data object Failed : CredentialOrderProgress
    data class Unknown(val rawValue: String) : CredentialOrderProgress
}

sealed interface CredentialPaymentStatus {
    data object NotRequired : CredentialPaymentStatus
    data object Pending : CredentialPaymentStatus
    data object Paid : CredentialPaymentStatus
    data class Unknown(val rawValue: String) : CredentialPaymentStatus
}

data class CredentialOrder(
    val id: String,
    val orderNumber: String,
    val serviceName: String,
    val email: String,
    val price: Double,
    val progress: CredentialOrderProgress,
    val paymentStatus: CredentialPaymentStatus,
    val appliedAt: String,
) {
    val maskedEmail: String get() = maskCredentialEmail(email)
}

data class CredentialOrderPage(
    val items: List<CredentialOrder>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

sealed interface CredentialSessionState {
    data object Unauthenticated : CredentialSessionState
    data object Authenticating : CredentialSessionState
    data object Ready : CredentialSessionState
    data object PortalLoginRequired : CredentialSessionState
    data object WebFallbackRequired : CredentialSessionState
    data class Failed(val message: String) : CredentialSessionState
}

enum class CredentialFailureKind {
    PortalLoginRequired,
    Authentication,
    NetworkUnavailable,
    ServiceMaintenance,
    SubmissionUncertain,
    Unknown,
}

class CredentialFailure(
    val kind: CredentialFailureKind,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

fun credentialOrderProgress(rawValue: String): CredentialOrderProgress = when (rawValue) {
    "0" -> CredentialOrderProgress.Pending
    "1" -> CredentialOrderProgress.Sent
    "2" -> CredentialOrderProgress.Failed
    else -> CredentialOrderProgress.Unknown(rawValue)
}

fun credentialPaymentStatus(rawValue: String, price: Double): CredentialPaymentStatus = when {
    price.compareTo(0.0) == 0 -> CredentialPaymentStatus.NotRequired
    rawValue == "0" -> CredentialPaymentStatus.Pending
    rawValue == "1" -> CredentialPaymentStatus.Paid
    else -> CredentialPaymentStatus.Unknown(rawValue)
}

fun maskCredentialEmail(email: String): String {
    val local = email.substringBefore('@')
    val domain = email.substringAfter('@', missingDelimiterValue = "")
    if (domain.isBlank()) return "***"
    return if (local.length <= 1) "*@$domain" else "${local.take(1)}***@$domain"
}

private fun String.isCredentialEmail(): Boolean =
    matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
