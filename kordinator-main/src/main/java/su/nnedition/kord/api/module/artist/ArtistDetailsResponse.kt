package su.nnedition.kord.api.module.artist

import su.nnedition.kord.api.module.album.AlbumSummaryResponse
import su.nnedition.kord.api.module.track.TrackResponse

data class ArtistDetailsResponse(
    val id: String,
    val name: String,
    val tracks: List<TrackResponse>,
    val albumCount: Int,
    val albums: List<AlbumSummaryResponse>,
)
