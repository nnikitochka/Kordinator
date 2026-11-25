package su.nnedition.kord

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import su.nnedition.kord.service.media.MediaScannerService
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/** Shared home-directory and scan helpers for the legacy integration tests. */
class TestCaseUtils private constructor() {
    companion object {
        private val log = LoggerFactory.getLogger(TestCaseUtils::class.java)
        private var kordinatorHomeDirForTest: java.nio.file.Path? = null

        @JvmStatic
        fun kordinatorHomePathForTest(): String {
            if (kordinatorHomeDirForTest == null) {
                try {
                    kordinatorHomeDirForTest = Files.createTempDirectory("kordinator_test_")
                } catch (failure: IOException) {
                    throw RuntimeException("Error while creating temporary KORDINATOR_HOME directory for tests", failure)
                }
                log.info("KORDINATOR_HOME directory will be {}", requireNotNull(kordinatorHomeDirForTest).toAbsolutePath())
            }
            return requireNotNull(kordinatorHomeDirForTest).toAbsolutePath().toString()
        }

        /** Cleans the test home without deleting the directory itself. */
        @JvmStatic
        @Throws(IOException::class)
        fun cleanKordinatorHomeForTest() {
            MoreFiles.deleteDirectoryContents(Paths.get(kordinatorHomePathForTest()), RecursiveDeleteOption.ALLOW_INSECURE)
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
