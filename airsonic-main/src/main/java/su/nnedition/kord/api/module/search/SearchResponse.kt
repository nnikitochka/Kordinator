package su.nnedition.kord.api.module.search

import su.nnedition.kord.api.module.track.TrackResponse

data class SearchResponse(
    val query: String,
    val offset: Int,
    val totalHits: Long,
    val tracks: List<TrackResponse>,
)