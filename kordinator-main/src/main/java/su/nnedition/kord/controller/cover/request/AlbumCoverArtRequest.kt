package su.nnedition.kord.controller.cover.request

import su.nnedition.kord.domain.Album
import su.nnedition.kord.domain.CoverArt
import su.nnedition.kord.service.CoverArtImageService

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
