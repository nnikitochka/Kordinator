package su.nnedition.kord.service.scrobbler

import java.time.Instant

internal data class ListenBrainzRegistration(
    val url: String,
    val token: String?,
    val artist: String?,
    val album: String?,
    val title: String?,
    val musicBrainzReleaseId: String?,
    val musicBrainzRecordingId: String?,
    val trackNumber: Int?,
    val duration: Int,
    val time: Instant,
    val submission: Boolean,
) {
    fun payload(): Map<String, Any> {
        val additional = mutableMapOf<String, Any>()
        musicBrainzReleaseId?.let { additional["release_mbid"] = it }
        musicBrainzRecordingId?.let { additional["recording_mbid"] = it }
        trackNumber?.let { additional["tracknumber"] = it }
        val metadata = mutableMapOf<String, Any>()
        if (additional.isNotEmpty()) metadata["additional_info"] = additional
        artist?.let { metadata["artist_name"] = it }
        title?.let { metadata["track_name"] = it }
        album?.let { metadata["release_name"] = it }
        val item = mutableMapOf<String, Any>()
        if (metadata.isNotEmpty()) item["track_metadata"] = metadata
        if (submission) item["listened_at"] = time.epochSecond
        return mapOf("listen_type" to if (submission) "single" else "playing_now", "payload" to listOf(item))
    }
}
