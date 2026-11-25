package su.nnedition.kord.api.module.share

import su.nnedition.kord.api.module.track.TrackResponse

data class ShareResponse(
    val id: Long,
    val trackIds: List<Long>,
    val description: String?,
    val createdAt: String,
    val expiresAt: String?,
    val visitCount: Long,
    val lastVisitedAt: String?,
    val token: String?,
    val tracks: List<TrackResponse>,
)
