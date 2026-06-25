package su.nnedition.kord.library

import su.nnedition.kord.data.domain.track.Track
import su.nnedition.kord.library.model.ArtistFolder

data class LibraryScanResult(
    val artists: List<ArtistFolder>,
)