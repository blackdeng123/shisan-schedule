package com.shisan.campuspro.core.network

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.pluginOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtClientTest {
    @Test
    fun `default client keeps cookies across login and probe requests`() {
        val client = HttpClientFactory.jwxt()
        try {
            assertNotNull(client.pluginOrNull(HttpCookies))
        } finally {
            client.close()
        }
    }

    @Test
    fun `encodeInp matches base64 format used by jwxt login`() {
        assertEquals("MTIzNDU2", JwxtEncoding.encodeInp("123456"))
        assertEquals("YWJjREVGMTIz", JwxtEncoding.encodeInp("abcDEF123"))
    }

    @Test
    fun `login success detects menu and frame markers`() {
        assertTrue(JwxtLoginDetector.isSuccess("<html>欢迎您 我的课表 成绩查询</html>", "https://jwxt.jsu.edu.cn/jsxsd/framework/xsMain.jsp"))
        assertFalse(JwxtLoginDetector.isSuccess("<html>用户名或密码错误</html>", "https://jwxt.jsu.edu.cn/jsxsd/xk/LoginToXk"))
    }

    @Test
    fun `login error returns user friendly message`() {
        assertEquals("用户名或密码错误", JwxtLoginDetector.errorMessage("<div>用户名或密码错误</div>"))
        assertEquals("需要验证码，请在电脑端登录后再试", JwxtLoginDetector.errorMessage("<div>验证码错误</div>"))
    }
}
