package com.shisan.campuspro.core.network

import java.util.Base64

object JwxtEncoding {
    fun encodeInp(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
}
