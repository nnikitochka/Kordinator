package su.nnedition.kord.api.module.artist

data class ArtistSummaryResponse(
    val id: String,
    val libraryId: Long,
    val name: String,
    val albumCount: Long,
    val trackCount: Long,
)
