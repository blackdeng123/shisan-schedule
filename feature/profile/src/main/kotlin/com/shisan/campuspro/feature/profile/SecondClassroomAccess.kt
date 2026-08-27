package com.shisan.campuspro.feature.profile

import com.shisan.campuspro.core.model.AuthState
import com.shisan.campuspro.core.model.LoginMode

const val SECOND_CLASSROOM_TARGET_URL =
    "https://xsfw.jsu.edu.cn/xsfw/sys/dektapp/*default/index.do#/cjdcx"

enum class SecondClassroomEntryDecision {
    Open,
    RequirePortalLogin,
}

fun secondClassroomEntryDecision(authState: AuthState): SecondClassroomEntryDecision =
    if (authState.isLoggedIn && authState.loginMode == LoginMode.PORTAL) {
        SecondClassroomEntryDecision.Open
    } else {
        SecondClassroomEntryDecision.RequirePortalLogin
    }

fun buildCastgcCookieHeader(castgc: String): String =
    "CASTGC=$castgc; Domain=authserver.jsu.edu.cn; Path=/authserver; Secure; HttpOnly"

fun buildExpiredCastgcCookieHeader(): String =
    "CASTGC=; Domain=authserver.jsu.edu.cn; Path=/authserver; Max-Age=0; Secure; HttpOnly"

fun shouldResumeSecondClassroom(pending: Boolean, loginMode: LoginMode): Boolean =
    pending && loginMode == LoginMode.PORTAL
