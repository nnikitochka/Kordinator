package org.airsonic.player.controller.cover.request

import org.airsonic.player.controller.cover.AutoCover
import org.airsonic.player.domain.CoverArt
import org.airsonic.player.service.CoverArtImageService
import org.airsonic.player.util.FileUtil
import org.slf4j.LoggerFactory
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.time.Instant
import java.util.function.Supplier
import javax.imageio.ImageIO

abstract class CoverArtRequest {
    protected val coverArtImageService: CoverArtImageService

    @JvmField
    var coverArt: CoverArt? = null

    protected var keyGenerator: Supplier<String?>? = null
    protected var lastModifiedGenerator: Supplier<Instant?>? = null

    constructor(coverArtImageService: CoverArtImageService) {
        this.coverArtImageService = coverArtImageService
    }

    constructor(
        coverArtImageService: CoverArtImageService,
        coverArt: CoverArt?,
        keyGenerator: Supplier<String?>,
        lastModifiedGenerator: Supplier<Instant?>,
    ) {
        this.coverArtImageService = coverArtImageService
        this.coverArt = if (CoverArt.NULL_ART == coverArt) null else coverArt
        this.keyGenerator = keyGenerator
        this.lastModifiedGenerator = lastModifiedGenerator
    }

    fun getKey(): String? = coverArt?.let { "${it.folderId}/${it.path}" } ?: requireNotNull(keyGenerator).get()

    fun lastModified(): Instant? = coverArt?.let {
        FileUtil.lastModified(requireNotNull(coverArtImageService.coverArtService.getFullPath(it)))
    } ?: requireNotNull(lastModifiedGenerator).get()

    open fun createImage(size: Int): BufferedImage {
        val selectedCoverArt = coverArt
        if (selectedCoverArt != null) {
            try {
                val input = coverArtImageService.getImageInputStream(selectedCoverArt)
                var reason: String
                if (input == null) {
                    reason = "getImageInputStream"
                } else {
                    input.use {
                        val image = ImageIO.read(it)
                        if (image != null) return CoverArtImageService.scale(image, size, size)
                    }
                    reason = "ImageIO.read"
                }
                LOG.warn("Failed to process cover art {}: {} failed", selectedCoverArt, reason)
            } catch (failure: Throwable) {
                LOG.warn("Failed to process cover art {}", selectedCoverArt, failure)
            }
        }
        return createAutoCover(size, size)
    }

    protected fun createAutoCover(width: Int, height: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics: Graphics2D = image.createGraphics()
        AutoCover(graphics, getKey(), getArtist(), getAlbum(), width, height).paintCover()
        graphics.dispose()
        return image
    }

    abstract fun getAlbum(): String?

    abstract fun getArtist(): String?

    companion object {
        private val LOG = LoggerFactory.getLogger(CoverArtRequest::class.java)
    }
}
