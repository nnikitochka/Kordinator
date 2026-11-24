package su.nnedition.kord.api.module.album

import su.nnedition.kord.api.module.track.TrackResponse

data class AlbumDetailsResponse(
    val id: String,
    val name: String,
    val artist: String?,
    val year: Int?,
    val genre: String?,
    val trackCount: Int,
    val durationSeconds: Long,
    val tracks: List<TrackResponse>,
)