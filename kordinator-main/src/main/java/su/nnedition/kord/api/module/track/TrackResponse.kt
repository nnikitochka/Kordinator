package su.nnedition.kord.api.module.track

data class TrackResponse(
    val id: Long,
    val libraryId: Long,
    val relativePath: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val durationSeconds: Int,
    val sizeBytes: Long,
    val modifiedAt: String?,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
)
