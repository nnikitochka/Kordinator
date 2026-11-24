package su.nnedition.kord.api.module.playlist

data class PlaylistTrackResponse(
    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationSeconds: Int,
)