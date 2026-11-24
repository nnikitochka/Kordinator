package org.airsonic.player.controller.cover.request

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Playlist
import org.airsonic.player.service.CoverArtImageService
import java.awt.Graphics2D
import java.awt.image.BufferedImage

open class PlaylistCoverArtRequest(
    coverArtImageService: CoverArtImageService,
    private val playlist: Playlist,
) : CoverArtRequest(
    coverArtImageService,
    null,
    { CoverArtImageService.PLAYLIST_COVERART_PREFIX + playlist.id },
    { playlist.changed },
) {
    private val imageService = coverArtImageService

    override fun getAlbum(): String? = null

    override fun getArtist(): String? = playlist.name

    override fun toString(): String = "Playlist ${playlist.id} - ${playlist.name}"

    override fun createImage(size: Int): BufferedImage {
        val albums = getRepresentativeAlbums()
        if (albums.isEmpty()) return createAutoCover(size, size)
        if (albums.size < 4) return MediaFileCoverArtRequest(imageService, albums.first()).createImage(size)

        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val graphics: Graphics2D = image.createGraphics()
        val half = size / 2
        graphics.drawImage(MediaFileCoverArtRequest(imageService, albums[0]).createImage(half), null, 0, 0)
        graphics.drawImage(MediaFileCoverArtRequest(imageService, albums[1]).createImage(half), null, half, 0)
        graphics.drawImage(MediaFileCoverArtRequest(imageService, albums[2]).createImage(half), null, 0, half)
        graphics.drawImage(MediaFileCoverArtRequest(imageService, albums[3]).createImage(half), null, half, half)
        graphics.dispose()
        return image
    }

    private fun getRepresentativeAlbums(): List<MediaFile> = imageService.playlistService.getFilesInPlaylist(playlist.id)
        .parallelStream()
        .map(imageService.mediaFileService::getParentOf)
        .filter { album -> album != null && !imageService.mediaFileService.isRoot(album) }
        .distinct()
        .toList()
}
