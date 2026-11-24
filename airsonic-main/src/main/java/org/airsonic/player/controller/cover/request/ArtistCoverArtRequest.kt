package org.airsonic.player.controller.cover.request

import org.airsonic.player.domain.Artist
import org.airsonic.player.domain.CoverArt
import org.airsonic.player.service.CoverArtImageService

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
