package su.nnedition.kord.service

/** Tag values requested for a media file update. */
data class TrackTagUpdate(
    val mediaFileId: Int,
    val track: Int?,
    val artist: String?,
    val album: String?,
    val title: String?,
    val year: Int?,
    val genre: String?,
)
