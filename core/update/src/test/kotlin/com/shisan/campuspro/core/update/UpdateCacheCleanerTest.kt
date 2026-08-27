package com.shisan.campuspro.core.update

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCacheCleanerTest {
    @Test
    fun `removes files at least seven days old and keeps recent files`() {
        val now = 1_800_000_000_000L
        val directory = createTempDirectory().toFile()
        val old = directory.resolve("old.apk").apply {
            writeText("old")
            setLastModified(now - 7L * 24 * 60 * 60 * 1000)
        }
        val recent = directory.resolve("recent.apk").apply {
            writeText("recent")
            setLastModified(now - 6L * 24 * 60 * 60 * 1000)
        }

        UpdateCacheCleaner.clean(directory, now)

        assertFalse(old.exists())
        assertTrue(recent.exists())
        directory.deleteRecursively()
    }
}
