package org.airsonic.player.service

import com.google.common.io.MoreFiles
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.User
import org.airsonic.player.util.StringUtil
import org.springframework.stereotype.Service

/** Prepares video playback data without depending on an HTTP controller or view. */
@Service
class VideoPlaybackService(
    private val files: MediaFileService,
    private val captions: CaptionService,
    private val jwt: JWTSecurityService,
    private val bookmarks: BookmarkService,
    private val settings: SettingsService,
) {
    fun load(file: MediaFile, user: User, playerId: Int, baseUrl: String, position: Long?): Map<String, Any?> {
        files.populateStarredDate(file, user.username)
        val preferences = settings.getUserSettings(user.username)
        val direct = isStreamable(file)
        val streams = linkedMapOf<String, String>()
        val stream = baseUrl + "stream?id=${file.id}&player=$playerId"
        val hls = baseUrl + "hls/hls.m3u8?id=${file.id}&player=$playerId"
        if (direct) streams["Original"] = "$stream&format=raw"
        BIT_RATES.forEach { rate ->
            streams["$rate Kbps"] = if (direct) "$stream&format=mp4&maxBitRate=$rate" else "$hls&maxBitRate=$rate"
        }
        val remote = if (direct) "ext/stream?id=${file.id}&player=$playerId&format=raw"
            else "ext/hls/hls.m3u8?id=${file.id}&player=$playerId"
        return mapOf(
            "videoId" to file.id, "videoTitle" to file.title, "duration" to (file.duration ?: -1.0),
            "starred" to (file.starredDate != null), "streamable" to direct,
            "castable" to (file.format?.lowercase() in setOf("mp4", "m4v", "mkv")),
            "streamUrls" to streams, "defaultBitRate" to if (direct) "Original" else "$DEFAULT_BIT_RATE Kbps",
            "captions" to captions.listCaptions(file, baseUrl),
            "remoteStreamUrl" to baseUrl + jwt.addJWTToken(user.username, remote),
            "remoteCoverArtUrl" to baseUrl + jwt.addJWTToken(user.username, "ext/coverArt.view?id=${file.id}"),
            "remoteCaptionsListUrl" to baseUrl + jwt.addJWTToken(user.username, "ext/rest/captions/list?id=${file.id}"),
            "position" to (position ?: bookmarks.getBookmark(user.username, file.id)?.positionMillis ?: 0L),
            "autoBookmark" to preferences.autoBookmark, "videoBookmarkFrequency" to preferences.videoBookmarkFrequency,
            "contentType" to if (direct) StringUtil.getMimeType(MoreFiles.getFileExtension(file.relativePath)) else "application/x-mpegurl",
            "canShare" to user.isShareRole, "canDownload" to user.isDownloadRole,
        )
    }

    fun isStreamable(file: MediaFile): Boolean {
        if (file.format?.lowercase() !in setOf("mp4", "m4v")) return false
        val metadata = captions.getVideoMetaData(file) ?: return true
        return metadata.videoTracks.firstOrNull()?.isStreamable != false &&
            metadata.audioTracks.firstOrNull()?.isStreamable != false
    }

    companion object {
        const val DEFAULT_BIT_RATE = 1500
        @JvmField val BIT_RATES = listOf(200, 300, 400, 500, 700, 1000, 1200, DEFAULT_BIT_RATE, 2000, 3000, 5000)
    }
}
