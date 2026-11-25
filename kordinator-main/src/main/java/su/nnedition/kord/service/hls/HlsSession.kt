package su.nnedition.kord.service.hls

import com.google.common.io.MoreFiles
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.VideoTranscodingSettings
import su.nnedition.kord.io.InputStreamReaderThread
import su.nnedition.kord.io.TranscodeInputStream
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.TranscodingService
import su.nnedition.kord.util.FileUtil
import su.nnedition.kord.util.Util
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.Objects
import java.util.SortedSet
import java.util.TreeSet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class HlsSession(
    private val sessionKey: Key,
    private val mediaFile: MediaFile,
    private val transcodingService: TranscodingService,
) {
    private val logger = LoggerFactory.getLogger(HlsSession::class.java.toString() + "-" + sessionKey.id())
    private var process: Process? = null
    private var destroySessionFuture: ScheduledFuture<*>? = null

    init { logger.info("Creating HLS session {}: {}", sessionKey.id(), sessionKey) }

    @Throws(Exception::class)
    fun waitForSegment(segmentIndex: Int, timeoutMillis: Long): Path? {
        logger.debug("Requesting hls segment {}", segmentIndex)
        scheduleSessionDestruction()
        var segment = getSegment(segmentIndex)
        if (segment != null) {
            logger.debug("Segment {} already produced.", segmentIndex)
            return segment
        }
        if (!isProcessAlive()) {
            startProcess(segmentIndex)
        } else {
            val latest = getLatestCompletedSegmentIndex()
            if (latest != null && (segmentIndex < latest || segmentIndex > latest + 2)) {
                destroySession()
                startProcess(segmentIndex)
            }
        }
        val timeout = currentTimeMillis() + timeoutMillis
        while (segment == null && currentTimeMillis() < timeout && isProcessAlive()) {
            logger.debug("Segment {} not yet produced. Waiting.", segmentIndex)
            Thread.sleep(2000L)
            segment = getSegment(segmentIndex)
        }
        if (segment == null && !isProcessAlive()) segment = getSegment(segmentIndex)
        if (segment != null) logger.debug("Segment {} produced.", segmentIndex)
        else logger.warn("Timed out for segment {}", segmentIndex)
        return segment
    }

    @Synchronized
    private fun scheduleSessionDestruction() {
        destroySessionFuture?.cancel(false)
        destroySessionFuture = EXECUTOR.schedule({ destroySession() }, SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    fun destroySession() {
        logger.debug("Destroying hls session")
        killProcess()
        FileUtil.delete(getDirectory())
    }

    private fun getSegment(segmentIndex: Int): Path? =
        if (getCompletedSegmentIndexes().contains(segmentIndex)) getDirectory().resolve("$segmentIndex.ts") else null

    private fun getLatestCompletedSegmentIndex(): Int? = getCompletedSegmentIndexes().let { if (it.isEmpty()) null else it.last() }

    private fun getCompletedSegmentIndexes(): SortedSet<Int> = try {
        Files.list(getDirectory()).use { children ->
            val result = children.filter(Files::isRegularFile).filter { MoreFiles.getFileExtension(it) == "ts" }
                .map { MoreFiles.getNameWithoutExtension(it).toInt() }
                .collect(Collectors.toCollection { TreeSet<Int>() })
            if (result.isNotEmpty() && isProcessAlive()) result.remove(result.last())
            result
        }
    } catch (failure: IOException) {
        logger.warn("Could not retrieve directory list for {} to find segment files", getDirectory(), failure)
        Collections.emptySortedSet()
    }

    private fun getDirectory(): Path {
        var dir = getHlsRootDirectory().resolve(sessionKey.id())
        if (!Files.exists(dir)) {
            try {
                dir = Files.createDirectories(dir)
                logger.info("Created hls cache {}", dir)
            } catch (failure: Exception) {
                logger.error("Failed to create hls cache {}", dir, failure)
            }
        }
        return dir
    }

    private fun startProcess(segmentIndex: Int) {
        val size = StringUtils.split(sessionKey.size, "x")
        val duration = sessionKey.duration!!
        val settings = VideoTranscodingSettings(
            size[0].toInt(), size[1].toInt(), segmentIndex * duration, duration.toDouble(),
            sessionKey.audioTrack ?: 1, segmentIndex, getDirectory().resolve("%d.ts").toString(),
            getDirectory().resolve("out.m3u8").toString(),
        )
        val parameters = transcodingService.getParameters(mediaFile, null, sessionKey.maxBitRate, "ts", settings)
        val input = transcodingService.getTranscodedInputStream(parameters) as TranscodeInputStream
        val running = input.process!!
        process = running
        InputStreamReaderThread(running.inputStream, javaClass.simpleName, true).start()
    }

    private fun killProcess() {
        process?.let { running ->
            logger.info("Killing hls process")
            try { running.destroy() } catch (failure: Exception) { logger.error("Failed to kill hls process", failure) }
        }
    }

    private fun isProcessAlive(): Boolean {
        val running = process ?: return false
        return try { running.exitValue(); false } catch (_: IllegalThreadStateException) { true }
    }

    private fun currentTimeMillis(): Long = System.nanoTime() / 1_000_000L

    class Key(
        val mediaFileId: Int,
        val playerId: String?,
        val maxBitRate: Int,
        val size: String?,
        val duration: Int?,
        val audioTrack: Int?,
    ) {
        fun id(): String = kotlin.math.abs(hashCode()).toString()
        override fun equals(other: Any?): Boolean = other is Key && mediaFileId == other.mediaFileId &&
            playerId == other.playerId && maxBitRate == other.maxBitRate && size == other.size &&
            duration == other.duration && audioTrack == other.audioTrack
        override fun hashCode(): Int = Objects.hash(mediaFileId, playerId, maxBitRate, size, duration, audioTrack)
        override fun toString(): String = "{mediaFileId=$mediaFileId, playerId='$playerId', maxBitRate='$maxBitRate', " +
            "size='$size', duration='$duration', audioTrack=$audioTrack}"
    }

    companion object {
        private val EXECUTOR = Executors.newSingleThreadScheduledExecutor(Util.getDaemonThreadfactory("hls-destroy-session"))
        private const val SESSION_TIMEOUT_SECONDS = 120L
        @JvmStatic fun getHlsRootDirectory(): Path = SettingsService.getKordinatorHome().resolve("hls")
    }
}
