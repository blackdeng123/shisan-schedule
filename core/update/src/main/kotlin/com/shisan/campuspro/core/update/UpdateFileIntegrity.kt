package com.shisan.campuspro.core.update

import java.io.File
import java.security.MessageDigest

object UpdateFileIntegrity {
    fun verify(file: File, expectedSizeBytes: Long, expectedSha256: String): Result<Unit> = runCatching {
        require(file.isFile) { "更新包不存在" }
        require(file.length() == expectedSizeBytes) { "更新包大小不匹配" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual.equals(expectedSha256, ignoreCase = true)) { "更新包 SHA-256 校验失败" }
    }
}
