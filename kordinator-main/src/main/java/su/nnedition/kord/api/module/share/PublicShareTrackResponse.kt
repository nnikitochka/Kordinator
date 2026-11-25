package su.nnedition.kord.api.module.share

import su.nnedition.kord.api.module.track.TrackResponse

data class PublicShareTrackResponse(
    val track: TrackResponse,
    val streamUrl: String,
)
