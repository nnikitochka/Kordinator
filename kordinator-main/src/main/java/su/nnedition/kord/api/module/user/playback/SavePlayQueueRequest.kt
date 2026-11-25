package su.nnedition.kord.api.module.user.playback

data class SavePlayQueueRequest(
    val trackIds: List<Long> = emptyList(),
    val currentTrackId: Long? = null,
    val positionMillis: Long = 0,
)
