package su.nnedition.kord.api.module.album

data class AlbumSummaryResponse(
    val id: String,
    val libraryId: Long,
    val name: String,
    val artist: String?,
    val year: Int?,
    val genre: String?,
    val trackCount: Long,
    val durationSeconds: Long,
    val releaseDate: String? = null,
)
