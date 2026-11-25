package su.nnedition.kord.api.module.video.bookmark

data class VideoBookmarkResponse(
    val videoId: Int,
    val positionMillis: Long,
    val comment: String?,
    val createdAt: String,
    val updatedAt: String,
)
