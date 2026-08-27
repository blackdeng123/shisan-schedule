package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.CredentialOrderProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialResponseParserTest {
    @Test
    fun `首页解析只暴露服务不暴露敏感基本信息`() {
        val json = """{"success":true,"result":{"basic_info":{"idNumber":"secret"},"service_list":{"records":[{"id":"s1","name":"成绩单","nameEn":"Transcript","status":"1","feesStatus":"1"}]}}}"""
        val result = CredentialResponseParser.dashboard(json)
        assertEquals(1, result.size)
        assertEquals("成绩单", result.single().name)
        assertTrue(result.single().nativeSupported)
        assertFalse(result.single().toString().contains("secret"))
    }

    @Test
    fun `订单解析保留未知处理状态`() {
        val json = """{"success":true,"result":{"records":[{"id":"1","orderno":"N1","serviceName":"成绩单","email":"a@example.com","price":0,"applyStatus":"future","payStatus":"0","applyTime":"now"}],"total":1,"current":1,"size":10}}"""
        val page = CredentialResponseParser.orders(json)
        assertEquals(CredentialOrderProgress.Unknown("future"), page.items.single().progress)
        assertEquals("*@example.com", page.items.single().maskedEmail)
    }

    @Test
    fun `失败响应抛出不包含完整响应的异常`() {
        val error = runCatching { CredentialResponseParser.dashboard("""{"success":false,"message":"登录超时","secret":"do-not-leak"}""") }.exceptionOrNull()
        assertEquals("登录超时", error?.message)
        assertFalse(error.toString().contains("do-not-leak"))
    }
}
