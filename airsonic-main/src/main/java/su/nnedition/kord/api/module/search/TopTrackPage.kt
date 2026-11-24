package su.nnedition.kord.api.module.search

import su.nnedition.kord.api.module.track.TrackResponse

data class TopTrackPage(
    val artist: String,
    val items: List<TrackResponse>,
    val count: Int,
)
