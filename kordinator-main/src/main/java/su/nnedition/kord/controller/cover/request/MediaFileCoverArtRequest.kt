package su.nnedition.kord.controller.cover.request

import su.nnedition.kord.domain.CoverArt
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.service.CoverArtImageService

open class MediaFileCoverArtRequest(
    coverArtImageService: CoverArtImageService,
    private val dir: MediaFile,
    private val proxyId: Int?,
) : CoverArtRequest(
    coverArtImageService,
    coverArtImageService.coverArtService.get(CoverArt.EntityType.MEDIA_FILE, requireNotNull(dir.id)),
    { "${dir.folderId}/${dir.path}" },
    { dir.changed },
) {
    constructor(coverArtImageService: CoverArtImageService, mediaFile: MediaFile) : this(coverArtImageService, mediaFile, null)

    override fun getAlbum(): String = dir.name

    override fun getArtist(): String? = dir.albumArtist ?: dir.artist

    override fun toString(): String = "Media file ${dir.id} - $dir${proxyId?.let { " (Proxy for $it)" }.orEmpty()}"
}
