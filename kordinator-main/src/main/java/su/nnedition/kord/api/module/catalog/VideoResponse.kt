package su.nnedition.kord.api.module.catalog

data class VideoResponse(
    val id: Int,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationSeconds: Int?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val format: String?,
)
