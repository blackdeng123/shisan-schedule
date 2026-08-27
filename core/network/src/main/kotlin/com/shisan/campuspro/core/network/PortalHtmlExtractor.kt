package com.shisan.campuspro.core.network

import org.jsoup.Jsoup

object PortalHtmlExtractor {
    private const val JsuDefaultPasswordSalt = "rjBFAaHsNkKAhpoi"

    fun casPasswordSalt(html: String): String? =
        inputValueById(html, "pwdEncryptSalt")
            ?: inputValueByName(html, "pwdEncryptSalt")
            ?: javascriptStringValue(html, "pwdEncryptSalt")
            ?: javascriptStringValue(html, "pwdDefaultEncryptSalt")
            ?: JsuDefaultPasswordSalt

    fun casExecution(html: String): String? =
        inputValueById(html, "execution")
            ?: inputValueByName(html, "execution")
            ?: javascriptStringValue(html, "execution")

    fun inputValueById(html: String, id: String): String? {
        val parsed = Jsoup.parse(html)
        parsed.selectFirst("input#$id")?.attr("value")?.let { return it }
        return inputValueRegex(id).find(html)?.groupValues?.getOrNull(1)
            ?: reversedInputValueRegex(id).find(html)?.groupValues?.getOrNull(1)
    }

    fun inputValueByName(html: String, name: String): String? {
        val parsed = Jsoup.parse(html)
        parsed.selectFirst("input[name=$name]")?.attr("value")?.let { return it }
        return inputNameValueRegex(name).find(html)?.groupValues?.getOrNull(1)
            ?: reversedInputNameValueRegex(name).find(html)?.groupValues?.getOrNull(1)
    }

    fun castgcFromSetCookie(setCookieValues: List<String>): String? =
        setCookieValues.firstNotNullOfOrNull { header ->
            header.split(';')
                .firstOrNull()
                ?.trim()
                ?.removePrefix("CASTGC=")
                ?.takeIf { it != header.split(';').firstOrNull()?.trim() }
        }

    fun errorMessage(html: String): String? {
        val parsed = Jsoup.parse(html)
        val error = parsed.selectFirst("#showErrorTip")?.text()?.trim().orEmpty()
        if (error.isNotBlank()) return error
        val captchaStyle = parsed.selectFirst("#captchaDiv")?.attr("style").orEmpty()
        if (captchaStyle.isBlank() || !captchaStyle.contains("display: none", ignoreCase = true)) {
            if (html.contains("captcha", ignoreCase = true) || html.contains("验证码")) return "需要验证码"
        }
        return null
    }

    private fun inputValueRegex(id: String): Regex =
        Regex("""<input[^>]*id=["']${Regex.escape(id)}["'][^>]*value=["']([^"']*)["']""")

    private fun reversedInputValueRegex(id: String): Regex =
        Regex("""<input[^>]*value=["']([^"']*)["'][^>]*id=["']${Regex.escape(id)}["']""")

    private fun inputNameValueRegex(name: String): Regex =
        Regex("""<input[^>]*name=["']${Regex.escape(name)}["'][^>]*value=["']([^"']*)["']""")

    private fun reversedInputNameValueRegex(name: String): Regex =
        Regex("""<input[^>]*value=["']([^"']*)["'][^>]*name=["']${Regex.escape(name)}["']""")

    private fun javascriptStringValue(html: String, variableName: String): String? =
        Regex("""(?:var|let|const)?\s*${Regex.escape(variableName)}\s*=\s*["']([^"']+)["']""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
}
