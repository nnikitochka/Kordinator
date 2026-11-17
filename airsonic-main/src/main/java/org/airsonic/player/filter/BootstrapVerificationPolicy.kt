package org.airsonic.player.filter

import java.nio.file.Files
import java.nio.file.Path

/** Framework-neutral check that the configured application home is usable. */
class BootstrapVerificationPolicy {
    fun verify(home: Path): Result {
        if (!Files.exists(home) || !Files.isDirectory(home)) {
            return Result.MISSING_OR_NOT_DIRECTORY
        }

        return try {
            val testFile = Files.createTempFile(home, "test", null)
            if (Files.exists(testFile) && Files.deleteIfExists(testFile)) Result.WRITABLE else Result.NOT_WRITABLE
        } catch (_: Exception) {
            Result.NOT_WRITABLE
        }
    }

    enum class Result {
        WRITABLE,
        MISSING_OR_NOT_DIRECTORY,
        NOT_WRITABLE,
    }
}
