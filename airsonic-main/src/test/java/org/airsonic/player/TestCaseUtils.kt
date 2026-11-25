package org.airsonic.player

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import org.airsonic.player.service.media.MediaScannerService
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/** Shared home-directory and scan helpers for the legacy integration tests. */
class TestCaseUtils private constructor() {
    companion object {
        private val log = LoggerFactory.getLogger(TestCaseUtils::class.java)
        private var airsonicHomeDirForTest: java.nio.file.Path? = null

        @JvmStatic
        fun airsonicHomePathForTest(): String {
            if (airsonicHomeDirForTest == null) {
                try {
                    airsonicHomeDirForTest = Files.createTempDirectory("airsonic_test_")
                } catch (failure: IOException) {
                    throw RuntimeException("Error while creating temporary AIRSONIC_HOME directory for tests", failure)
                }
                log.info("AIRSONIC_HOME directory will be {}", requireNotNull(airsonicHomeDirForTest).toAbsolutePath())
            }
            return requireNotNull(airsonicHomeDirForTest).toAbsolutePath().toString()
        }

        /** Cleans the test home without deleting the directory itself. */
        @JvmStatic
        @Throws(IOException::class)
        fun cleanAirsonicHomeForTest() {
            MoreFiles.deleteDirectoryContents(Paths.get(airsonicHomePathForTest()), RecursiveDeleteOption.ALLOW_INSECURE)
        }

        @JvmStatic
        fun waitForScanFinish(mediaScannerService: MediaScannerService) {
            while (mediaScannerService.isScanning) {
                try {
                    Thread.sleep(100)
                } catch (interrupted: InterruptedException) {
                    interrupted.printStackTrace()
                }
            }
        }

        @JvmStatic
        fun execScan(mediaScannerService: MediaScannerService) {
            mediaScannerService.scanLibrary()
            waitForScanFinish(mediaScannerService)
        }
    }
}
