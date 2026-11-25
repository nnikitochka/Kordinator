package org.airsonic.player.service.jukebox

data class JukeboxState(
    val status: String,
    val currentIndex: Int,
    val positionSeconds: Int,
    val gain: Float,
    val tracks: List<JukeboxQueueTrackResponse>,
)