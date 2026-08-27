package com.shisan.campuspro.core.network

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object PortalCasCrypto {
    private val secureRandom = SecureRandom()
    private const val randomChars = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"

    fun encryptPassword(password: String, salt: String): String {
        require(salt.toByteArray(Charsets.UTF_8).size == 16) { "门户密码盐值必须为 16 字节" }
        val plainText = randomString(64) + password
        val iv = randomString(16)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8)),
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)))
    }

    fun randomString(length: Int): String = buildString(length) {
        repeat(length) {
            append(randomChars[secureRandom.nextInt(randomChars.length)])
        }
    }
}

