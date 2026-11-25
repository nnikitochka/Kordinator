package su.nnedition.kord.api.module.video.playback

data class CreateVideoPlaybackRequest(
    val bitrateKbps: Int = 2_000,
    val width: Int = 1280,
    val height: Int = 720,
    val audioTrack: Int? = null,
)
