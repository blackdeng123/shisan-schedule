package com.shisan.campuspro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialModelsTest {
    @Test
    fun `订单邮箱默认脱敏`() {
        assertEquals("c***@example.com", maskCredentialEmail("campus@example.com"))
        assertEquals("*@example.com", maskCredentialEmail("a@example.com"))
    }

    @Test
    fun `邮箱配置校验主地址抄送标题和正文`() {
        assertTrue(CredentialEmailDraft("a@example.com", "", "成绩单", "请查收").validationErrors().isEmpty())
        assertFalse(CredentialEmailDraft("bad", "", "成绩单", "请查收").validationErrors().isEmpty())
        assertFalse(CredentialEmailDraft("a@example.com", "bad;ok@example.com", "成绩单", "请查收").validationErrors().isEmpty())
        assertFalse(CredentialEmailDraft("a@example.com", "", "x".repeat(61), "请查收").validationErrors().isEmpty())
        assertFalse(CredentialEmailDraft("a@example.com", "", "成绩单", "x".repeat(1001)).validationErrors().isEmpty())
    }

    @Test
    fun `未知订单状态保留服务端原值`() {
        assertEquals(CredentialOrderProgress.Sent, credentialOrderProgress("1"))
        assertEquals(CredentialOrderProgress.Failed, credentialOrderProgress("2"))
        assertEquals(CredentialOrderProgress.Pending, credentialOrderProgress("0"))
        assertEquals(CredentialOrderProgress.Unknown("future"), credentialOrderProgress("future"))
    }
}
