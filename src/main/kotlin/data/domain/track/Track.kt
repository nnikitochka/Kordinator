package su.nnedition.kord.data.domain.track

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import su.nnedition.kord.data.domain.artist.Artist

@Serializable
class Track(
    val id: Long,
    val title: String,
    val artists: List<Artist>,
    val relativePath: String,
    val releaseDate: String,
    @Transient
    val meta: TrackMeta? = null,
)