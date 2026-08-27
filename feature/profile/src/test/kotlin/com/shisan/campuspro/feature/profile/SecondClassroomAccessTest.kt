package com.shisan.campuspro.feature.profile

import com.shisan.campuspro.core.model.AuthState
import com.shisan.campuspro.core.model.LoginMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SecondClassroomAccessTest {
    @Test
    fun `second classroom opens the transcript query route`() {
        assertEquals(
            "https://xsfw.jsu.edu.cn/xsfw/sys/dektapp/*default/index.do#/cjdcx",
            SECOND_CLASSROOM_TARGET_URL,
        )
    }

    @Test
    fun `only a logged in portal session opens second classroom directly`() {
        assertEquals(
            SecondClassroomEntryDecision.Open,
            secondClassroomEntryDecision(AuthState(true, "20260001", "用户", LoginMode.PORTAL)),
        )
        assertEquals(
            SecondClassroomEntryDecision.RequirePortalLogin,
            secondClassroomEntryDecision(AuthState(true, "20260001", "用户", LoginMode.JWXT_DIRECT)),
        )
        assertEquals(
            SecondClassroomEntryDecision.RequirePortalLogin,
            secondClassroomEntryDecision(AuthState(false, "", "", null)),
        )
    }

    @Test
    fun `CASTGC cookie header keeps the required security attributes`() {
        assertEquals(
            "CASTGC=TGT-secret; Domain=authserver.jsu.edu.cn; Path=/authserver; Secure; HttpOnly",
            buildCastgcCookieHeader("TGT-secret"),
        )
    }

    @Test
    fun `expired CASTGC header removes the same scoped cookie`() {
        assertEquals(
            "CASTGC=; Domain=authserver.jsu.edu.cn; Path=/authserver; Max-Age=0; Secure; HttpOnly",
            buildExpiredCastgcCookieHeader(),
        )
    }

    @Test
    fun `pending second classroom navigation resumes only after portal login`() {
        assertEquals(true, shouldResumeSecondClassroom(true, LoginMode.PORTAL))
        assertEquals(false, shouldResumeSecondClassroom(true, LoginMode.JWXT_DIRECT))
        assertEquals(false, shouldResumeSecondClassroom(false, LoginMode.PORTAL))
    }
}
