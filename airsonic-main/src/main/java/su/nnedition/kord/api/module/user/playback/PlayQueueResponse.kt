package su.nnedition.kord.api.module.user.playback

data class PlayQueueResponse(
    val trackIds: List<Long>,
    val currentTrackId: Long?,
    val positionMillis: Long,
    val updatedAt: String?,
    val tracks: List<PlayQueueTrackResponse>,
)
