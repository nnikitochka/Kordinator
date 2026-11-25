package su.nnedition.kord.domain

data class AlbumNotes(
    val notes: String?,
    val musicBrainzId: String,
    val lastFmUrl: String,
    val smallImageUrl: String,
    val mediumImageUrl: String,
    val largeImageUrl: String,
)
