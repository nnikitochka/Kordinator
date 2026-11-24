package su.nnedition.kord.api.module.playlist

data class PlaylistResponse(
    val id: Long,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
    val visibility: String,
    val description: String?,
    val trackIds: List<Long>,
    val tracks: List<PlaylistTrackResponse>,
)
