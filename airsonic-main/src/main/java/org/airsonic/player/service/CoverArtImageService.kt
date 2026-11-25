package org.airsonic.player.service

import com.google.common.io.MoreFiles
import org.airsonic.player.controller.cover.request.AlbumCoverArtRequest
import org.airsonic.player.controller.cover.request.ArtistCoverArtRequest
import org.airsonic.player.controller.cover.request.CoverArtRequest
import org.airsonic.player.controller.cover.request.MediaFileCoverArtRequest
import org.airsonic.player.controller.cover.request.PlaylistCoverArtRequest
import org.airsonic.player.controller.cover.request.VideoCoverArtRequest
import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.domain.Album
import org.airsonic.player.domain.Artist
import org.airsonic.player.domain.CoverArt
import org.airsonic.player.domain.CoverArtScheme
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Playlist
import org.airsonic.player.domain.Transcoding
import org.airsonic.player.domain.VideoTranscodingSettings
import org.airsonic.player.service.metadata.JaudiotaggerParser
import org.airsonic.player.util.FileUtil
import org.airsonic.player.util.StringUtil
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.tuple.Pair
import org.jaudiotagger.tag.images.Artwork
import org.slf4j.LoggerFactory
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Semaphore
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.stream.ImageOutputStream

/** Produces and caches cover art images. */
class CoverArtImageService(
    @JvmField val mediaFileService: MediaFileService,
    @JvmField val coverArtService: CoverArtService,
    private val transcodingService: TranscodingService,
    private val settingsService: SettingsService,
    @JvmField val playlistService: PlaylistService,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val jaudiotaggerParser: JaudiotaggerParser,
) {
    private lateinit var semaphore: Semaphore

    fun init() {
        semaphore = Semaphore(settingsService.getCoverArtConcurrency())
    }

    fun getLastModified(id: String?, offset: String?): Long {
        val request = createCoverArtRequest(id, offset)
        return request?.lastModified()?.toEpochMilli() ?: -1L
    }

    @JvmRecord
    data class ImageResult(val stream: InputStream, val contentType: String) : AutoCloseable {
        @Throws(IOException::class)
        override fun close() = stream.close()
    }

    @Throws(IOException::class)
    fun getImage(id: String?, size: Int?, offset: String?): ImageResult {
        val request = createCoverArtRequest(id, offset)
        LOG.trace("getImage - {}", request)
        if (request == null) return fallback(size)

        try {
            if (size == null && request.coverArt != null) {
                val image = getImageInputStreamWithType(coverArtService.getFullPath(request.coverArt))
                return ImageResult(image.left, image.right)
            }
            val imageSize = size ?: CoverArtScheme.LARGE.size * 2
            val cachedImage = getCachedImage(request, imageSize)
            return ImageResult(
                Files.newInputStream(cachedImage),
                StringUtil.getMimeType(MoreFiles.getFileExtension(cachedImage)),
            )
        } catch (failure: Exception) {
            LOG.debug("Sending fallback as an exception was encountered during normal cover art processing", failure)
            return fallback(size)
        }
    }

    @Throws(IOException::class)
    private fun fallback(size: Int?): ImageResult {
        val imageBytes = ByteArrayOutputStream().use { output ->
            javaClass.getResourceAsStream("/org/airsonic/player/controller/default_cover.jpg").use { input ->
                var image = ImageIO.read(input)
                if (size != null) image = scale(image, size, size)
                ImageIO.write(image, "jpeg", output)
            }
            output.toByteArray()
        }
        return ImageResult(ByteArrayInputStream(imageBytes), StringUtil.getMimeType("jpeg"))
    }

    private fun createCoverArtRequest(id: String?, offset: String?): CoverArtRequest? {
        if (id == null) return null
        return when {
            id.startsWith(ALBUM_COVERART_PREFIX) -> createAlbumCoverArtRequest(id.removePrefix(ALBUM_COVERART_PREFIX).toInt())
            id.startsWith(ARTIST_COVERART_PREFIX) -> createArtistCoverArtRequest(id.removePrefix(ARTIST_COVERART_PREFIX).toInt())
            id.startsWith(PLAYLIST_COVERART_PREFIX) -> createPlaylistCoverArtRequest(id.removePrefix(PLAYLIST_COVERART_PREFIX).toInt())
            else -> createMediaFileCoverArtRequest(id.toInt(), offset)
        }
    }

    private fun createAlbumCoverArtRequest(id: Int): CoverArtRequest? =
        albumDao.getAlbum(id)?.let { AlbumCoverArtRequest(this, it) }

    private fun createArtistCoverArtRequest(id: Int): CoverArtRequest? =
        artistDao.getArtist(id)?.let { ArtistCoverArtRequest(this, it) }

    private fun createPlaylistCoverArtRequest(id: Int): PlaylistCoverArtRequest? =
        playlistService.getPlaylist(id)?.let { PlaylistCoverArtRequest(this, it) }

    private fun createMediaFileCoverArtRequest(id: Int, offset: String?): CoverArtRequest? {
        val mediaFile = mediaFileService.getMediaFile(id) ?: return null
        if (mediaFile.isVideo) {
            val parsedOffset = offset?.takeIf { it.isNotBlank() }?.trim()?.toInt() ?: 60
            return VideoCoverArtRequest(this, mediaFile, parsedOffset)
        }
        val directory = if (mediaFile.isDirectory) mediaFile else mediaFileService.getParentOf(mediaFile)
        return MediaFileCoverArtRequest(this, directory, if (mediaFile.isDirectory) null else mediaFile.id)
    }

    @Throws(IOException::class)
    private fun getCachedImage(request: CoverArtRequest, size: Int): Path {
        val hash = DigestUtils.md5Hex(request.getKey())
        val encoding = if (request.coverArt != null) "jpeg" else "png"
        val cachedImage = getImageCacheDirectory(size).resolve("$hash.$encoding")

        synchronized(hash.intern()) {
            if (!Files.exists(cachedImage) || request.lastModified()!!.isAfter(FileUtil.lastModified(cachedImage))) {
                var writer: ImageWriter? = null
                try {
                    Files.newOutputStream(cachedImage).use { output: OutputStream ->
                        output.buffered().use { buffered ->
                            ImageIO.createImageOutputStream(buffered).use { imageOutput: ImageOutputStream ->
                                semaphore.acquire()
                                val image = request.createImage(size)
                                    ?: throw IOException("Unable to decode image.")
                                writer = ImageIO.getImageWritersByFormatName(encoding).next()
                                val params = writer!!.defaultWriteParam
                                params.compressionMode = ImageWriteParam.MODE_EXPLICIT
                                params.compressionQuality = (settingsService.getCoverArtQuality() / 100.0).toFloat()
                                writer!!.output = imageOutput
                                writer!!.write(null, IIOImage(image, null, null), params)
                            }
                        }
                    }
                } catch (failure: Throwable) {
                    LOG.warn("Failed to create thumbnail for {}", request, failure)
                    FileUtil.delete(cachedImage)
                    throw IOException("Failed to create thumbnail for $request. ${failure.message}")
                } finally {
                    writer?.dispose()
                    semaphore.release()
                }
            }
        }
        return cachedImage
    }

    @Throws(IOException::class)
    fun getImageInputStream(art: CoverArt): InputStream =
        getImageInputStreamWithType(coverArtService.getFullPath(art)).left

    @Throws(IOException::class)
    private fun getImageInputStreamWithType(file: Path?): Pair<InputStream, String> {
        val imageFile = file!!
        if (jaudiotaggerParser.isApplicable(imageFile)) {
            LOG.trace("Using Jaudio Tagger for reading artwork from {}", imageFile)
            return try {
                LOG.trace("Reading artwork from file {}", imageFile)
                val artwork: Artwork = jaudiotaggerParser.getEmbeddedArtwork(imageFile)!!
                Pair.of(ByteArrayInputStream(artwork.binaryData), artwork.mimeType)
            } catch (failure: Exception) {
                LOG.debug("Could not read artwork from file {}", imageFile)
                throw RuntimeException(failure)
            }
        }
        val input = BufferedInputStream(Files.newInputStream(imageFile))
        return Pair.of(input, StringUtil.getMimeType(MoreFiles.getFileExtension(imageFile)))
    }

    @Throws(Exception::class)
    fun getImageInputStreamForVideo(mediaFile: MediaFile, width: Int, height: Int, offset: Int): InputStream {
        val videoSettings = VideoTranscodingSettings(width, height, offset, 0.0)
        val parameters = TranscodingService.Parameters(mediaFile, videoSettings)
        parameters.transcoding = Transcoding(null, null, null, null, settingsService.getVideoImageCommand(), null, null, false)
        return transcodingService.getTranscodedInputStream(parameters)
    }

    @Synchronized
    private fun getImageCacheDirectory(size: Int): Path {
        val directory = SettingsService.getAirsonicHome().resolve("thumbs").resolve(size.toString())
        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory)
                LOG.info("Created thumbnail cache {}", directory)
            } catch (failure: Exception) {
                LOG.error("Failed to create thumbnail cache {}", directory, failure)
            }
        }
        return directory
    }

    companion object {
        const val ALBUM_COVERART_PREFIX = "al-"
        const val ARTIST_COVERART_PREFIX = "ar-"
        const val PLAYLIST_COVERART_PREFIX = "pl-"
        private val LOG = LoggerFactory.getLogger(CoverArtImageService::class.java)

        @JvmStatic
        fun scale(thumb: BufferedImage, width: Int, height: Int): BufferedImage {
            var image = thumb
            var imageWidth = image.width
            var imageHeight = image.height
            do {
                imageWidth /= 2
                imageHeight /= 2
                if (imageWidth < width) imageWidth = width
                if (imageHeight < height) imageHeight = height
                val resized = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB)
                val graphics: Graphics2D = resized.createGraphics()
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                graphics.drawImage(image, 0, 0, resized.width, resized.height, null)
                graphics.dispose()
                image = resized
            } while (imageWidth != width)
            return image
        }
    }
}
