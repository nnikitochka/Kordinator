package org.airsonic.player.ajax.artist

import org.airsonic.player.ajax.media.MediaFileEntry
import org.airsonic.player.domain.ArtistBio

/**
 * Artist details sent to the authenticated WebSocket user.
 *
 * Nullable properties deliberately preserve the previous Java record's JVM
 * contract: callers may construct the response with absent values, and the
 * JavaBean getters remain `getSimilarArtists()`, `getArtistBio()`, and
 * `getTopSongs()`.
 */
data class ArtistInfo(
    val similarArtists: List<SimilarArtist>?,
    val artistBio: ArtistBio?,
    val topSongs: List<MediaFileEntry>?,
)
