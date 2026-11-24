package su.nnedition.kord.api.module.favorite.track

import su.nnedition.kord.api.module.track.TrackResponse

data class FavoriteTrackPage(
    val items: List<TrackResponse>,
    val count: Int,
    val total: Long,
    val limit: Int,
    val offset: Int,
)
