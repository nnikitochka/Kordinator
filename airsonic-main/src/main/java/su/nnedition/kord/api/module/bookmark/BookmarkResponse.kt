package su.nnedition.kord.api.module.bookmark

import su.nnedition.kord.api.module.track.TrackResponse

data class BookmarkResponse(
    val track: TrackResponse,
    val positionMillis: Long,
    val comment: String?,
    val createdAt: String,
    val updatedAt: String,
)