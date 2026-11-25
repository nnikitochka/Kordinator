package su.nnedition.kord.controller.cover.request

import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.service.CoverArtImageService
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

open class VideoCoverArtRequest(
    coverArtImageService: CoverArtImageService,
    private val mediaFile: MediaFile,
    private val offset: Int,
) : CoverArtRequest(coverArtImageService) {
    init {
        keyGenerator = java.util.function.Supplier { "${mediaFile.folderId}/${mediaFile.path}/$offset" }
        lastModifiedGenerator = java.util.function.Supplier { mediaFile.changed }
    }

    override fun createImage(size: Int): BufferedImage {
        val height = size
        val width = height * 16 / 9
        try {
            val input = coverArtImageService.getImageInputStreamForVideo(mediaFile, width, height, offset)
            val result = input.use { ImageIO.read(it) }
            if (result != null) return result
            LOG.warn("Failed to process cover art for {}: {}", mediaFile, null)
        } catch (failure: Throwable) {
            LOG.warn("Failed to process cover art for {}", mediaFile, failure)
        }
        return createAutoCover(width, height)
    }

    override fun getAlbum(): String? = null

    override fun getArtist(): String = mediaFile.name

    override fun toString(): String = "Video file ${mediaFile.id} - $mediaFile"

    companion object {
        private val LOG = LoggerFactory.getLogger(VideoCoverArtRequest::class.java)
    }
}
