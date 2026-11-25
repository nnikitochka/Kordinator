package su.nnedition.kord.api.module.video.caption

data class VideoCaptionResponse(
    val id: String,
    val location: String,
    val format: String,
    val language: String,
    val href: String,
)
