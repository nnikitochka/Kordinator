package su.nnedition.kord.api.module.track.tag

data class EditTrackTagsRequest(
    val artist: String? = null,
    val album: String? = null,
    val title: String? = null,
    val genre: String? = null,
    val track: Int? = null,
    val year: Int? = null,
    val releaseDate: String? = null,
)
