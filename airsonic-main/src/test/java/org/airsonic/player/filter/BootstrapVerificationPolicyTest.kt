package org.airsonic.player.filter

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class BootstrapVerificationPolicyTest {
    private val policy = BootstrapVerificationPolicy()

    @Test
    fun acceptsWritableDirectory() {
        val home = Files.createTempDirectory("airsonic-home")
        try {
            assertEquals(BootstrapVerificationPolicy.Result.WRITABLE, policy.verify(home))
            Files.list(home).use { files -> assertEquals(0, files.count()) }
        } finally {
            Files.delete(home)
        }
    }

    @Test
    fun rejectsMissingPathAndRegularFile() {
        val parent = Files.createTempDirectory("airsonic-home")
        val missing = parent.resolve("missing")
        val file = Files.createFile(parent.resolve("home-file"))
        try {
            assertEquals(BootstrapVerificationPolicy.Result.MISSING_OR_NOT_DIRECTORY, policy.verify(missing))
            assertEquals(BootstrapVerificationPolicy.Result.MISSING_OR_NOT_DIRECTORY, policy.verify(file))
        } finally {
            Files.delete(file)
            Files.delete(parent)
        }
    }
}
