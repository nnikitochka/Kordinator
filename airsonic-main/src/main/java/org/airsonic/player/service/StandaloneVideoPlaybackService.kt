package org.airsonic.player.service

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.hls.HlsSession
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class VideoPlaybackSession(
    val id: String,
    val videoId: Int,
    val playlistUrl: String,
    val expiresAt: String,
)

data class VideoPlaybackSegment(val stream: InputStream, val contentLength: Long)

/** Owns bearer-scoped, short lived HLS sessions for standalone video playback. */
class StandaloneVideoPlaybackService(
    private val mediaFileService: MediaFileService,
    private val transcodingService: TranscodingService,
) : AutoCloseable {
    private data class Session(
        val ownerId: Long,
        val mediaFile: MediaFile,
        val engine: HlsSession,
        val id: String,
        val playCounted: AtomicBoolean = AtomicBoolean(false),
        @Volatile var expiresAt: Instant,
    )

    private val sessions = ConcurrentHashMap<String, Session>()

    fun create(ownerId: Long, mediaFile: MediaFile, bitrate: Int, width: Int, height: Int, audioTrack: Int?): VideoPlaybackSession {
        require(ownerId > 0) { "Invalid playback owner" }
        require(mediaFile.isVideo) { "Only videos can start video playback sessions" }
        require(bitrate in 64..20_000) { "Bitrate must be between 64 and 20000 kbps" }
        require(width in 2..4096 && height in 2..4096 && width % 2 == 0 && height % 2 == 0) {
            "Video dimensions must be even values between 2 and 4096"
        }
        require(audioTrack == null || audioTrack in 1..64) { "Audio track must be between 1 and 64" }
        reapExpired()
        require(sessions.values.count { it.ownerId == ownerId } < MAX_SESSIONS_PER_USER) {
            "Too many active video sessions"
        }
        val id = UUID.randomUUID().toString()
        val key = HlsSession.Key(mediaFile.id!!, id, bitrate, "${width}x$height", SEGMENT_SECONDS, audioTrack)
        val session = Session(ownerId, mediaFile, HlsSession(key, mediaFile, transcodingService),
            id, expiresAt = Instant.now().plus(SESSION_TTL))
        sessions[id] = session
        return session.toResponse()
    }

    fun playlist(ownerId: Long, id: String, accessibleLibraryIds: Set<Long>?): Pair<VideoPlaybackSession, String>? {
        val session = find(ownerId, id, accessibleLibraryIds) ?: return null
        val duration = session.mediaFile.duration!!.coerceAtLeast(0.0)
        val segmentCount = kotlin.math.ceil(duration / SEGMENT_SECONDS).toInt()
        val builder = StringBuilder()
            .append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:")
            .append(SEGMENT_SECONDS).append("\n#EXT-X-MEDIA-SEQUENCE:0\n")
        repeat(segmentCount) { index ->
            val segmentDuration = (duration - index * SEGMENT_SECONDS).coerceAtMost(SEGMENT_SECONDS.toDouble())
            builder.append("#EXTINF:").append("%.3f".format(java.util.Locale.ROOT, segmentDuration))
                .append(",\nsegments/").append(index).append(".ts\n")
        }
        builder.append("#EXT-X-ENDLIST\n")
        return session.toResponse() to builder.toString()
    }

    fun segment(ownerId: Long, id: String, index: Int, accessibleLibraryIds: Set<Long>?): VideoPlaybackSegment? {
        require(index >= 0) { "Segment index must be non-negative" }
        val session = find(ownerId, id, accessibleLibraryIds) ?: return null
        val count = kotlin.math.ceil(session.mediaFile.duration!!.coerceAtLeast(0.0) / SEGMENT_SECONDS).toInt()
        require(index < count) { "Segment index is outside the video duration" }
        val file = session.engine.waitForSegment(index, SEGMENT_TIMEOUT.toMillis())
            ?: throw java.util.concurrent.TimeoutException("Timed out producing video segment")
        if (index == 0 && session.playCounted.compareAndSet(false, true)) {
            mediaFileService.incrementPlayCount(session.mediaFile)
        }
        return VideoPlaybackSegment(java.nio.file.Files.newInputStream(file), java.nio.file.Files.size(file))
    }

    fun close(ownerId: Long, id: String): Boolean {
        val session = sessions[id]?.takeIf { it.ownerId == ownerId } ?: return false
        if (sessions.remove(id, session)) session.engine.destroySession()
        return true
    }

    private fun find(ownerId: Long, id: String, accessibleLibraryIds: Set<Long>?): Session? {
        val session = sessions[id] ?: return null
        if (session.ownerId != ownerId) return null
        if (session.expiresAt.isBefore(Instant.now())) {
            if (sessions.remove(id, session)) session.engine.destroySession()
            return null
        }
        val libraryId = session.mediaFile.folderId!!.toLong()
        if (accessibleLibraryIds != null && libraryId !in accessibleLibraryIds) return null
        session.expiresAt = Instant.now().plus(SESSION_TTL)
        return session
    }

    private fun reapExpired() {
        val now = Instant.now()
        sessions.entries.forEach { (id, session) ->
            if (session.expiresAt.isBefore(now) && sessions.remove(id, session)) session.engine.destroySession()
        }
    }

    override fun close() {
        sessions.values.forEach { it.engine.destroySession() }
        sessions.clear()
    }

    private fun Session.toResponse() = VideoPlaybackSession(
        id,
        requireNotNull(mediaFile.id),
        "/api/v1/videos/${mediaFile.id}/playback-sessions/$id/playlist.m3u8",
        expiresAt.toString(),
    )

    private companion object {
        const val SEGMENT_SECONDS = 10
        const val MAX_SESSIONS_PER_USER = 4
        val SESSION_TTL: Duration = Duration.ofMinutes(30)
        val SEGMENT_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
