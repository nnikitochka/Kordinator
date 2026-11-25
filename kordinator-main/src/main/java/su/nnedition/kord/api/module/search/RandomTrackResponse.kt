package su.nnedition.kord.api.module.search

import su.nnedition.kord.api.module.track.TrackResponse

data class RandomTrackResponse(
    val items: List<TrackResponse>,
    val count: Int,
)
