package su.nnedition.kord.controller.cover.request

import su.nnedition.kord.domain.Artist
import su.nnedition.kord.domain.CoverArt
import su.nnedition.kord.service.CoverArtImageService

open class ArtistCoverArtRequest(
    coverArtImageService: CoverArtImageService,
    private val artist: Artist,
) : CoverArtRequest(
    coverArtImageService,
    coverArtImageService.coverArtService.get(CoverArt.EntityType.ARTIST, artist.id),
    { CoverArtImageService.ARTIST_COVERART_PREFIX + artist.id },
    { artist.lastScanned },
) {
    override fun getAlbum(): String? = null

    override fun getArtist(): String? = artist.name

    override fun toString(): String = "Artist ${artist.id} - ${artist.name}"
}
