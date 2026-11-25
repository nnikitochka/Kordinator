package org.airsonic.player.service.jukebox

data class JukeboxQueueTrackResponse(
    val trackId: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationSeconds: Int?,
)