package su.nnedition.kord.service.jukebox

import su.nnedition.kord.domain.MediaFile

data class JukeboxQueueTrack(
    val trackId: Long,
    val mediaFile: MediaFile,
)