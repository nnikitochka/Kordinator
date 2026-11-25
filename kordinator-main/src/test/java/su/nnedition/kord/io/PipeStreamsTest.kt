package su.nnedition.kord.io

import com.google.common.io.Resources
import com.google.common.util.concurrent.RateLimiter
import su.nnedition.kord.domain.TransferStatus
import su.nnedition.kord.io.PipeStreams.MonitoredInputStream
import su.nnedition.kord.io.PipeStreams.PipedInputStream
import su.nnedition.kord.io.PipeStreams.PipedOutputStream
import su.nnedition.kord.util.FileUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.fail
import org.junit.Test
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths

class PipeStreamsTest {
    @Test
    fun pipeStreamsTransferCompleteRequestedBlocksAndTrackStatus() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17)
        val status = TransferStatus(null)
        val input = PipedInputStream(null, 5)
        val monitored = MonitoredInputStream(input, null, { status }, {}, { _, _ -> })
        val output = PipedOutputStream(input)

        try {
            Thread {
                try {
                    output.write(bytes, 4, 12)
                    output.close()
                } catch (failure: java.io.IOException) {
                    fail("Should not throw IOException")
                }
            }.start()

            val twoBytes = ByteArray(2)
            assertThat(monitored.read(twoBytes)).isEqualTo(2)
            assertThat(twoBytes).containsExactly(4, 5)
            assertThat(status.bytesTransferred).isEqualTo(2)
            assertThat(status.bytesSkipped).isEqualTo(0)

            assertThat(monitored.skip(4)).isEqualTo(4)
            assertThat(twoBytes).containsExactly(4, 5)
            assertThat(status.bytesTransferred).isEqualTo(2)
            assertThat(status.bytesSkipped).isEqualTo(4)

            assertThat(monitored.read(twoBytes)).isEqualTo(2)
            assertThat(twoBytes).containsExactly(10, 11)
            assertThat(status.bytesTransferred).isEqualTo(4)
            assertThat(status.bytesSkipped).isEqualTo(4)

            assertThat(monitored.read()).isEqualTo(12)
            assertThat(twoBytes).containsExactly(10, 11)
            assertThat(status.bytesTransferred).isEqualTo(5)
            assertThat(status.bytesSkipped).isEqualTo(4)

            val fiveBytes = ByteArray(5)
            assertThat(monitored.read(fiveBytes)).isEqualTo(3)
            assertThat(fiveBytes).containsExactly(13, 14, 15, 0, 0)
            assertThat(status.bytesTransferred).isEqualTo(8)
            assertThat(status.bytesSkipped).isEqualTo(4)

            assertThat(monitored.read(fiveBytes)).isEqualTo(-1)
            assertThat(fiveBytes).containsExactly(13, 14, 15, 0, 0)
            assertThat(status.bytesTransferred).isEqualTo(8)
            assertThat(status.bytesSkipped).isEqualTo(4)

            assertThat(monitored.skip(4)).isZero()
            assertThat(status.bytesTransferred).isEqualTo(8)
            assertThat(status.bytesSkipped).isEqualTo(4)
            assertThat(monitored.read()).isEqualTo(-1)
            assertThat(status.bytesTransferred).isEqualTo(8)
            assertThat(status.bytesSkipped).isEqualTo(4)
        } finally {
            output.close()
            monitored.close()
            input.close()
        }
    }

    @Test
    fun monitoredInputStreamTracksCallbacksAndLimitsReadsButNotSkips() {
        val status = TransferStatus(null)
        val limiter = RateLimiter.create(4.0)
        val file = Paths.get(Resources.getResource("MEDIAS/piano.mp3").toURI())
        val events = mutableSetOf<String>()
        assertThat(Files.size(file)).isEqualTo(FileUtil.size(file))
        assertThat(events).isEmpty()

        val stream = MonitoredInputStream(
            Files.newInputStream(file),
            limiter,
            { status },
            { if (!events.add("statusClosed")) fail("statusClosed multiple times") },
            { _, _ -> if (!events.add("inputStreamOpened")) fail("inputStream opened multiple times") },
        )
        try {
            assertThat(events).containsOnly("inputStreamOpened")
            val start = System.currentTimeMillis()
            repeat(6) { stream.skip(2) }
            assertThat(System.currentTimeMillis() - start).isLessThan(1000)
            assertThat(status.bytesTransferred).isZero()
            assertThat(status.bytesSkipped).isEqualTo(12)
            assertThat(events).containsOnly("inputStreamOpened")

            val bytes = ByteArray(2)
            val readStart = System.currentTimeMillis()
            repeat(6) { stream.read(bytes) }
            repeat(2) { stream.read() }
            assertThat(System.currentTimeMillis() - readStart).isGreaterThan(2000)
            assertThat(status.bytesTransferred).isEqualTo(14)
            assertThat(status.bytesSkipped).isEqualTo(12)
            assertThat(events).containsOnly("inputStreamOpened")
        } finally {
            stream.close()
        }

        assertThat(events).containsOnly("inputStreamOpened", "statusClosed")
    }
}
