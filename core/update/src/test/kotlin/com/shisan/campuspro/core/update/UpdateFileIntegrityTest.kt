package com.shisan.campuspro.core.update

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateFileIntegrityTest {
    @Test
    fun `matching size and sha passes`() {
        val file = temporaryFile("verified apk bytes")
        val result = UpdateFileIntegrity.verify(
            file = file,
            expectedSizeBytes = file.length(),
            expectedSha256 = sha256(file),
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `wrong size or hash fails`() {
        val file = temporaryFile("tampered apk bytes")

        assertTrue(UpdateFileIntegrity.verify(file, file.length() + 1, sha256(file)).isFailure)
        assertTrue(UpdateFileIntegrity.verify(file, file.length(), "0".repeat(64)).isFailure)
    }

    private fun temporaryFile(content: String): File =
        kotlin.io.path.createTempFile(suffix = ".apk").toFile().apply {
            writeText(content)
            deleteOnExit()
        }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }
}
