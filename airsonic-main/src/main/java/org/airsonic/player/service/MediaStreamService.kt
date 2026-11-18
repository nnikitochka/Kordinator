package org.airsonic.player.service

import org.airsonic.player.dao.PlayerDaoPlayQueueFactory
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.PlayStatus
import org.airsonic.player.domain.TransferStatus
import org.airsonic.player.domain.User
import org.airsonic.player.domain.VideoTranscodingSettings
import org.airsonic.player.io.PipeStreams.MonitoredInputStream
import org.airsonic.player.io.PipeStreams.PipedInputStream
import org.airsonic.player.io.PipeStreams.PipedOutputStream
import org.airsonic.player.io.PlayQueueInputStream
import org.airsonic.player.io.ShoutCastOutputStream
import org.airsonic.player.security.CallerContext
import org.airsonic.player.security.AuthorizationDeniedException
import org.airsonic.player.util.FileUtil
import org.airsonic.player.util.StringUtil
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Optional
import java.util.function.BiConsumer
import java.util.function.BiPredicate
import java.util.function.Consumer
import java.util.function.Function
import kotlin.math.roundToLong

/** Creates monitored streams shared by REST and website playback. */
class MediaStreamService(
    private val statusService: StatusService,
    private val securityService: SecurityService,
    private val settingsService: SettingsService,
    private val transcodingService: TranscodingService,
    private val audioScrobblerService: AudioScrobblerService,
    private val mediaFileService: MediaFileService,
    private val searchService: SearchService,
    private val playQueueFactory: PlayerDaoPlayQueueFactory,
) {
    data class VideoOptions(val maxBitRate: String?, val timeOffset: String?, val duration: String?, val size: String?)
    data class Result(val stream: InputStream, val length: Long?, val contentType: String, val headers: Map<String, List<String>>)

    fun open(jwtAuthentication: Boolean, callerContext: CallerContext, player: Player, user: User, format: String?, suffix: String?,
             maxBitRate: Optional<Int>, id: Optional<Int>, path: Optional<String>, offsetSeconds: Double?,
             request: VideoOptions, icyMetadata: String?, notModified: BiPredicate<String?, Long>): Result? {
        if (!jwtAuthentication && !user.isStreamRole) {
            throw AuthorizationDeniedException("Streaming is forbidden for user ${user.username}")
        }
        var expectedSize: Long? = null
        var outputSuffix = suffix
        val bitRate = maxBitRate.filter { it != 0 }.orElse(null)
        var videoSettings: VideoTranscodingSettings? = null
        val file = path.map { mediaFileService.getMediaFile(it) }.orElseGet {
            id.map { mediaFileService.getMediaFile(it) }.orElse(null)
        }
        var byteOffset: Long? = null
        if (file != null) {
            if (!jwtAuthentication && !securityService.isFolderAccessAllowed(file, callerContext)) {
                throw AuthorizationDeniedException("Access to file ${file.id} is forbidden for user ${user.username}")
            }
            player.playQueue.index = player.playQueue.files.indexOf(file)
            val queue = playQueueFactory.createPlayQueue()
            queue.addFiles(true, file)
            player.playQueue = queue
            if (file.isVideo && format != TranscodingService.FORMAT_RAW) {
                videoSettings = createVideoTranscodingSettings(file, request)
            }
            val parameters = transcodingService.getParameters(file, player, bitRate, format, videoSettings)
            expectedSize = if (parameters.isRangeAllowed) parameters.expectedLength else null
            val duration = file.duration
            if (expectedSize != null && expectedSize > 0 && offsetSeconds != null && offsetSeconds > 0 && duration != null) {
                byteOffset = (expectedSize * offsetSeconds / duration).roundToLong()
                expectedSize = maxOf(0, expectedSize - byteOffset)
            }
            if (notModified.test(expectedSize?.toString(), file.changed.toEpochMilli())) return null
            outputSuffix = transcodingService.getSuffix(player, file, format)
        } else {
            statusService.getStreamStatusesForPlayer(player).forEach { it.terminate() }
        }
        val criteria = player.playQueue.randomSearchCriteria
        if (player.playQueue.index == -1 && criteria != null) {
            player.playQueue.addFiles(false, searchService.getRandomSongs(criteria))
            LOG.info("Recreated random playlist with {} songs.", player.playQueue.size())
        }
        val status = statusService.createStreamStatus(player)
        val fileStart = Consumer<MediaFile> { media ->
            LOG.info("{}: {} listening to {} in folder {}", player.ipAddress, player.username,
                FileUtil.getShortPath(media.relativePath), media.folderId)
            mediaFileService.incrementPlayCount(media)
            scrobble(media, player, false)
            status.mediaFile = media
            statusService.addActiveLocalPlay(PlayStatus(status.id, media, player, status.millisSinceLastUpdate))
        }
        val fileEnd = Consumer<MediaFile> { media ->
            scrobble(media, player, true)
            statusService.removeActiveLocalPlay(PlayStatus(status.id, media, player, status.millisSinceLastUpdate))
        }
        val selectedVideoSettings = videoSettings
        val generator = Function<MediaFile, InputStream> { media ->
            transcodingService.getTranscodedInputStream(
                transcodingService.getParameters(media, player, bitRate, format, selectedVideoSettings))
        }
        val headers = linkedMapOf<String, List<String>>()
        var playStream: InputStream = PlayQueueInputStream(player.playQueue, fileStart, fileEnd, generator)
        var streamInit = BiConsumer<InputStream, TransferStatus> { _, _ -> }
        if (icyMetadata == "1") {
            expectedSize = null
            val details = shoutcastDetails(playStream)
            playStream = details.stream
            streamInit = details.init
            headers.putAll(details.headers)
        }
        byteOffset?.let { offset ->
            val previous = streamInit
            streamInit = BiConsumer { input, transfer ->
                previous.accept(input, transfer)
                transfer.addBytesSkipped(input.skip(offset))
            }
        }
        expectedSize?.let { playStream = ThresholdInputStream(playStream, it) }
        val monitored = MonitoredInputStream(playStream, settingsService.downloadBitrateLimiter, { status }, { transfer ->
            securityService.updateUserByteCounts(user, transfer.bytesTransferred, 0L, 0L)
            statusService.removeStreamStatus(transfer)
        }, streamInit)
        return Result(monitored, expectedSize, StringUtil.getMimeType(outputSuffix), headers)
    }

    private fun scrobble(media: MediaFile, player: Player, submission: Boolean) {
        if (player.clientId == null) audioScrobblerService.register(media, player.username, submission, null)
    }

    private data class ShoutcastDetails(val stream: InputStream, val init: BiConsumer<InputStream, TransferStatus>,
                                       val headers: Map<String, List<String>>)

    private fun shoutcastDetails(input: InputStream): ShoutcastDetails {
        val headers = mapOf("icy-metaint" to listOf(ShoutCastOutputStream.META_DATA_INTERVAL.toString()),
            "icy-notice1" to listOf("This stream is served using Airsonic"),
            "icy-notice2" to listOf("Airsonic - Free media streamer"), "icy-name" to listOf("Airsonic"),
            "icy-genre" to listOf("Mixed"), "icy-url" to listOf("https://airsonic.github.io/"))
        val init = BiConsumer<InputStream, TransferStatus> { source, status ->
            val pipe = source as PipedInputStream
            Thread({
                try {
                    input.use { original ->
                        PipedOutputStream(pipe).use { output ->
                            ShoutCastOutputStream(output) { status.mediaFile?.title ?: settingsService.welcomeTitle }.use { shout ->
                                original.copyTo(shout)
                            }
                        }
                    }
                } catch (e: Exception) {
                    LOG.debug("Error with output to Shoutcast stream", e)
                }
            }, "ShoutcastStreamDatafeed").start()
            while (pipe.source == null) { Thread.onSpinWait() }
        }
        return ShoutcastDetails(PipedInputStream(), init, headers)
    }

    private class ThresholdInputStream(input: InputStream, private val threshold: Long) : FilterInputStream(input) {
        private var bytesRead = 0L
        override fun read(): Int {
            var value = super.read()
            if (value == -1 && bytesRead < threshold) value = 0
            else if (value != -1 && bytesRead == threshold) warn()
            bytesRead++
            return value
        }
        override fun read(bytes: ByteArray, off: Int, len: Int): Int {
            var count = super.read(bytes, off, len)
            if (count == -1 && bytesRead < threshold) {
                count = minOf(len.toLong(), threshold - bytesRead).toInt()
                java.util.Arrays.fill(bytes, off, off + count, 0.toByte())
            } else if (count != -1 && bytesRead <= threshold && bytesRead + count > threshold) warn()
            bytesRead += count
            return count
        }
        private fun warn() {
            LOG.warn("Stream output exceeded expected length of {}. It is likely that the transcoder is not adhering to the bitrate limit or the media source is corrupted or has grown larger", threshold)
        }
    }

    private fun createVideoTranscodingSettings(file: MediaFile, request: VideoOptions): VideoTranscodingSettings {
        val maxBitRate = request.maxBitRate?.trim()?.takeIf { it.isNotEmpty() }?.toInt()
        val offset = request.timeOffset?.trim()?.takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val duration = request.duration?.trim()?.takeIf { it.isNotEmpty() }?.toDouble()
            ?: (file.duration?.minus(offset) ?: Double.MAX_VALUE)
        val size = getRequestedVideoSize(request.size)
            ?: TranscodingService.getSuitableVideoSize(file.width, file.height, maxBitRate!!)
        return VideoTranscodingSettings(size.width, size.height, offset, duration)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MediaStreamService::class.java)
        @JvmStatic fun getRequestedVideoSize(sizeSpec: String?): Dimension? {
            val match = sizeSpec?.let { Regex("^(\\d+)x(\\d+)$").matchEntire(it) } ?: return null
            val width = match.groupValues[1].toInt()
            val height = match.groupValues[2].toInt()
            return if (width <= 2000 && height <= 2000) Dimension(width, height) else null
        }
    }
}
