package org.airsonic.player.service.jukebox

import org.airsonic.player.domain.MediaFile

data class JukeboxQueueTrack(
    val trackId: Long,
    val mediaFile: MediaFile,
)