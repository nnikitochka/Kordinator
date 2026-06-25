package su.nnedition.kord.data.domain.album

data class Album(
    val id: Long,
    val title: String,
    val releaseDate: String,
    val totalTracks: Int,
)