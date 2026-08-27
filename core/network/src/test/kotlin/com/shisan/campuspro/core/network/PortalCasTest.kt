package com.shisan.campuspro.core.network

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalCasTest {
    @Test
    fun `extract input value by id tolerates attribute order`() {
        val html = """
            <input id="pwdEncryptSalt" value="1234567890abcdef" type="hidden">
            <input value="e1s1" type="hidden" id="execution">
            <input id="lt" type="hidden" value="LT-abc">
        """.trimIndent()

        assertEquals("1234567890abcdef", PortalHtmlExtractor.inputValueById(html, "pwdEncryptSalt"))
        assertEquals("e1s1", PortalHtmlExtractor.inputValueById(html, "execution"))
        assertEquals("LT-abc", PortalHtmlExtractor.inputValueById(html, "lt"))
    }

    @Test
    fun `extract cas salt from javascript variables`() {
        val html = """
            <script>
                var pwdDefaultEncryptSalt = "fedcba0987654321";
            </script>
        """.trimIndent()

        assertEquals("fedcba0987654321", PortalHtmlExtractor.casPasswordSalt(html))
    }

    @Test
    fun `extract cas execution from name only hidden input`() {
        val html = """
            <form id="casLoginForm">
                <input type="hidden" name="lt" id="lt" value="" />
                <input type="hidden" name="execution" value="e1s1"/>
                <input type="hidden" name="_eventId" value="submit"/>
            </form>
        """.trimIndent()

        assertEquals("e1s1", PortalHtmlExtractor.casExecution(html))
    }

    @Test
    fun `cas salt falls back to jsu default value`() {
        val html = "<html><body>login</body></html>"

        assertEquals("rjBFAaHsNkKAhpoi", PortalHtmlExtractor.casPasswordSalt(html))
    }

    @Test
    fun `encrypt password returns base64 ciphertext without openssl salt prefix`() {
        val encrypted = PortalCasCrypto.encryptPassword("password123", "1234567890abcdef")

        assertFalse(encrypted.startsWith("U2FsdGVkX1"))
        assertTrue(Base64.getDecoder().decode(encrypted).isNotEmpty())
    }

    @Test
    fun `encrypt password uses random prefix and iv`() {
        val first = PortalCasCrypto.encryptPassword("password123", "1234567890abcdef")
        val second = PortalCasCrypto.encryptPassword("password123", "1234567890abcdef")

        assertNotEquals(first, second)
    }

    @Test
    fun `extract castgc from set cookie values`() {
        val cookies = listOf(
            "SESSION=abc; Path=/; HttpOnly",
            "CASTGC=TGT-123456; Path=/authserver; Secure; HttpOnly",
        )

        assertEquals("TGT-123456", PortalHtmlExtractor.castgcFromSetCookie(cookies))
    }
}
