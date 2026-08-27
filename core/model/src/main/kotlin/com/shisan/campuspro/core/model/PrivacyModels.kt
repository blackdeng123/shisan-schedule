package com.shisan.campuspro.core.model

const val CurrentPrivacyPolicyVersion = "1.0"

data class PrivacyConsentState(
    val acceptedPolicyVersion: String? = null,
    val acceptedAtMillis: Long? = null,
) {
    fun isAccepted(currentVersion: String = CurrentPrivacyPolicyVersion): Boolean =
        acceptedPolicyVersion == currentVersion && acceptedAtMillis != null
}

fun canUseOnlineFeatures(
    privacyReady: Boolean,
    consentState: PrivacyConsentState,
    currentVersion: String = CurrentPrivacyPolicyVersion,
): Boolean = privacyReady && consentState.isAccepted(currentVersion)
