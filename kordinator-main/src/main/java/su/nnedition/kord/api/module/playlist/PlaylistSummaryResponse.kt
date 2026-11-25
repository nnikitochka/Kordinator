package su.nnedition.kord.api.module.playlist

data class PlaylistSummaryResponse(
    val id: Long,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
    val trackCount: Int,
    val visibility: String,
    val description: String?,
)
