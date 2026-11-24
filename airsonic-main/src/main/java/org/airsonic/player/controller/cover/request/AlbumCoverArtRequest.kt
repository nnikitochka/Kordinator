package org.airsonic.player.controller.cover.request

import org.airsonic.player.domain.Album
import org.airsonic.player.domain.CoverArt
import org.airsonic.player.service.CoverArtImageService

open class AlbumCoverArtRequest(
    coverArtImageService: CoverArtImageService,
    private val album: Album,
) : CoverArtRequest(
    coverArtImageService,
    coverArtImageService.coverArtService.get(CoverArt.EntityType.ALBUM, album.id),
    { CoverArtImageService.ALBUM_COVERART_PREFIX + album.id },
    { album.lastScanned },
) {
    override fun getAlbum(): String? = album.name

    override fun getArtist(): String? = album.artist

    override fun toString(): String = "Album ${album.id} - ${album.name}"
}
