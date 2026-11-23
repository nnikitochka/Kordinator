package org.airsonic.player.service

import de.umass.lastfm.Album as LastFmAlbum
import de.umass.lastfm.ImageSize
import org.airsonic.player.domain.CoverArt
import org.airsonic.player.domain.MediaFile
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.imageio.ImageIO

data class ArtworkSearchResult(val imageUrl: String, val artist: String, val album: String)
data class StoredArtwork(val relativePath: String, val contentType: String, val sizeBytes: Int)

interface ArtworkManagementService {
    fun setDirectoryArtwork(directoryId: Int, bytes: ByteArray): StoredArtwork
}

/** Searches the artwork provider and returns only entries with a usable image URL. */
class StandaloneArtworkSearchService(
    private val imageDownloader: (URI) -> ByteArray = ::downloadLastFmArtwork,
    private val searchProvider: (String) -> List<ArtworkSearchResult> = ::searchLastFm,
) {
    fun search(artist: String?, album: String?): List<ArtworkSearchResult> {
        val cleanArtist = artist?.trim()?.takeIf(String::isNotEmpty)
        val cleanAlbum = album?.trim()?.takeIf(String::isNotEmpty)
        require(cleanArtist != null || cleanAlbum != null) { "Artist or album is required" }
        require((cleanArtist?.length ?: 0) <= MAX_QUERY_LENGTH && (cleanAlbum?.length ?: 0) <= MAX_QUERY_LENGTH) {
            "Artist or album is too long"
        }
        val query = listOfNotNull(cleanArtist, cleanAlbum).joinToString(" ")
        return runCatching { searchProvider(query).take(MAX_RESULTS) }.getOrDefault(emptyList())
    }

    fun downloadSelectedArtwork(artist: String?, album: String?, imageUrl: String): ByteArray {
        val uri = runCatching { URI(imageUrl) }.getOrElse { throw IllegalArgumentException("Artwork URL is invalid", it) }
        require(uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null && uri.port in listOf(-1, 443)) {
            "Artwork URL must use HTTPS"
        }
        require(uri.host?.lowercase() in ALLOWED_ARTWORK_HOSTS) { "Artwork host is not supported" }
        require(search(artist, album).any { it.imageUrl == uri.toASCIIString() }) {
            "Artwork URL is not one of the search results"
        }
        val bytes = imageDownloader(uri)
        require(bytes.isNotEmpty() && bytes.size <= MAX_ARTWORK_BYTES) { "Artwork must be between 1 byte and 20 MiB" }
        return bytes
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 200
        const val MAX_RESULTS = 50
        const val MAX_ARTWORK_BYTES = 20 * 1024 * 1024
        val ALLOWED_ARTWORK_HOSTS = setOf("lastfm.freetls.fastly.net", "lastfm-img2.akamaized.net")
        const val LAST_FM_API_KEY = "ece4499898a9440896dfdce5dab26bbf"

        fun downloadLastFmArtwork(uri: URI): ByteArray {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
            val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { body ->
                require(response.statusCode() == 200) { "Artwork provider returned HTTP ${response.statusCode()}" }
                val bytes = body.readNBytes(MAX_ARTWORK_BYTES + 1)
                require(bytes.size <= MAX_ARTWORK_BYTES) { "Artwork must not exceed 20 MiB" }
                return bytes
            }
        }

        fun searchLastFm(query: String): List<ArtworkSearchResult> =
            LastFmAlbum.search(query, LAST_FM_API_KEY).mapNotNull { album ->
                val url = ImageSize.values().reversed()
                    .firstNotNullOfOrNull { size -> album.getImageURL(size)?.takeIf(String::isNotBlank) }
                    ?: return@mapNotNull null
                val artistName = album.artist?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val albumName = album.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                ArtworkSearchResult(url, artistName, albumName)
            }
    }
}

/** Stores validated cover files inside an accessible media directory. */
class StandaloneArtworkManagementService(
    private val mediaFileService: MediaFileService,
    private val mediaFolderService: MediaFolderService,
    private val coverArtService: CoverArtService,
) : ArtworkManagementService {
    override fun setDirectoryArtwork(directoryId: Int, bytes: ByteArray): StoredArtwork {
        require(bytes.isNotEmpty() && bytes.size <= MAX_ARTWORK_BYTES) { "Artwork must be between 1 byte and 20 MiB" }
        val directory = mediaFileService.getMediaFile(directoryId)
        require(directory != null && directory.isDirectory) { "Directory does not exist" }
        val folderId = requireNotNull(directory.folderId) { "Directory has no library" }
        val folder = requireNotNull(mediaFolderService.getMusicFolderById(folderId, true, true)) { "Library does not exist" }
        val libraryRoot = folder.path.toRealPath()
        val realDirectory = directory.getFullPath(folder.path).toRealPath()
        require(realDirectory.startsWith(libraryRoot) && Files.isDirectory(realDirectory)) { "Directory is outside its library" }

        val format = validateImage(bytes)
        val extension = when (format) {
            "jpeg" -> "jpg"
            "png" -> "png"
            else -> throw IllegalArgumentException("Only JPEG and PNG artwork is supported")
        }
        val target = realDirectory.resolve("cover.$extension")
        val temporary = Files.createTempFile(realDirectory, ".kordinator-cover-", ".$extension")
        try {
            Files.write(temporary, bytes)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        val relativePath = libraryRoot.relativize(target).toString().replace('\\', '/')
        coverArtService.upsert(CoverArt.EntityType.MEDIA_FILE, directoryId, relativePath, folderId, true)
        return StoredArtwork(relativePath, if (format == "jpeg") "image/jpeg" else "image/png", bytes.size)
    }

    private fun validateImage(bytes: ByteArray): String {
        try {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull()
                    ?: throw IllegalArgumentException("Artwork is not a supported image")
                try {
                    reader.setInput(input, true, true)
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    require(width > 0 && height > 0 && width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION &&
                        width.toLong() * height <= MAX_IMAGE_PIXELS) { "Artwork dimensions are too large" }
                    return reader.formatName.lowercase().let { format -> if (format == "jpg") "jpeg" else format }
                } finally {
                    reader.dispose()
                }
            }
        } catch (failure: IOException) {
            throw IllegalArgumentException("Artwork could not be decoded", failure)
        }
    }

    private companion object {
        const val MAX_ARTWORK_BYTES = 20 * 1024 * 1024
        const val MAX_IMAGE_DIMENSION = 10_000
        const val MAX_IMAGE_PIXELS = 40_000_000L
    }
}
