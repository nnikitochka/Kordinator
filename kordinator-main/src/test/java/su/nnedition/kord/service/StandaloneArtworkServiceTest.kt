package su.nnedition.kord.service

import su.nnedition.kord.domain.CoverArt
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.net.URI
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StandaloneArtworkServiceTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun storesValidatedArtworkWithinTheSelectedDirectoryAndUpdatesCoreCoverReference() {
        val albumDirectory = Files.createDirectory(root.resolve("Album"))
        val mediaFiles = Mockito.mock(MediaFileService::class.java)
        val folders = Mockito.mock(MediaFolderService::class.java)
        val coverArt = Mockito.mock(CoverArtService::class.java)
        val directory = MediaFile().apply {
            id = 55
            folderId = 3
            path = "Album"
            mediaType = MediaFile.MediaType.DIRECTORY
        }
        val folder = MusicFolder(3, root, "Music", MusicFolder.Type.MEDIA, true, Instant.now())
        Mockito.`when`(mediaFiles.getMediaFile(55)).thenReturn(directory)
        Mockito.`when`(folders.getMusicFolderById(3, true, true)).thenReturn(folder)

        val bytes = png(32, 24)
        val stored = StandaloneArtworkManagementService(mediaFiles, folders, coverArt).setDirectoryArtwork(55, bytes)

        assertEquals("Album/cover.png", stored.relativePath)
        assertEquals("image/png", stored.contentType)
        assertEquals(bytes.size, stored.sizeBytes)
        assertTrue(bytes.contentEquals(Files.readAllBytes(albumDirectory.resolve("cover.png"))))
        Mockito.verify(coverArt).upsert(CoverArt.EntityType.MEDIA_FILE, 55, "Album/cover.png", 3, true)
    }

    @Test
    fun storesAnimatedGifArtworkWithItsNativeExtensionAndContentType() {
        val albumDirectory = Files.createDirectory(root.resolve("Gif Album"))
        val mediaFiles = Mockito.mock(MediaFileService::class.java)
        val folders = Mockito.mock(MediaFolderService::class.java)
        val coverArt = Mockito.mock(CoverArtService::class.java)
        val directory = MediaFile().apply {
            id = 57
            folderId = 3
            path = "Gif Album"
            mediaType = MediaFile.MediaType.DIRECTORY
        }
        Mockito.`when`(mediaFiles.getMediaFile(57)).thenReturn(directory)
        Mockito.`when`(folders.getMusicFolderById(3, true, true)).thenReturn(
            MusicFolder(3, root, "Music", MusicFolder.Type.MEDIA, true, Instant.now()),
        )

        val bytes = encodedImage("gif")
        val stored = StandaloneArtworkManagementService(mediaFiles, folders, coverArt).setDirectoryArtwork(57, bytes)

        assertEquals("Gif Album/cover.gif", stored.relativePath)
        assertEquals("image/gif", stored.contentType)
        assertTrue(bytes.contentEquals(Files.readAllBytes(albumDirectory.resolve("cover.gif"))))
        Mockito.verify(coverArt).upsert(CoverArt.EntityType.MEDIA_FILE, 57, "Gif Album/cover.gif", 3, true)
    }

    @Test
    fun rejectsInvalidImagesAndDirectoriesOutsideTheLibrary() {
        val outside = Files.createDirectory(root.resolveSibling("${root.fileName}-outside"))
        val mediaFiles = Mockito.mock(MediaFileService::class.java)
        val folders = Mockito.mock(MediaFolderService::class.java)
        val coverArt = Mockito.mock(CoverArtService::class.java)
        val directory = MediaFile().apply {
            id = 56
            folderId = 3
            path = outside.toString()
            mediaType = MediaFile.MediaType.DIRECTORY
        }
        Mockito.`when`(mediaFiles.getMediaFile(56)).thenReturn(directory)
        Mockito.`when`(folders.getMusicFolderById(3, true, true)).thenReturn(
            MusicFolder(3, root, "Music", MusicFolder.Type.MEDIA, true, Instant.now()),
        )
        val service = StandaloneArtworkManagementService(mediaFiles, folders, coverArt)
        assertFailsWith<IllegalArgumentException> { service.setDirectoryArtwork(56, png(8, 8)) }
        assertFailsWith<IllegalArgumentException> { service.setDirectoryArtwork(56, byteArrayOf(1, 2, 3)) }
        Mockito.verifyNoInteractions(coverArt)
    }

    @Test
    fun trimsAndCombinesArtistAndAlbumForArtworkSearch() {
        var receivedQuery: String? = null
        val expected = listOf(ArtworkSearchResult("https://images.example/cover.jpg", "Artist", "Album"))
        val service = StandaloneArtworkSearchService { query ->
            receivedQuery = query
            expected
        }

        assertEquals(expected, service.search(" Artist ", " Album "))
        assertEquals("Artist Album", receivedQuery)
        assertFailsWith<IllegalArgumentException> { service.search(" ", " ") }
    }

    @Test
    fun searchesByArtistOrAlbumIndependently() {
        val queries = mutableListOf<String>()
        val service = StandaloneArtworkSearchService(searchProvider = { query -> queries += query; emptyList() })

        service.search(" Artist ", null)
        service.search(null, " Album ")

        assertEquals(listOf("Artist", "Album"), queries)
        assertFailsWith<IllegalArgumentException> { service.search(null, " ") }
    }

    @Test
    fun onlyDownloadsAnExactHttpsLastFmArtworkResult() {
        val selectedUrl = "https://lastfm.freetls.fastly.net/i/u/cover.jpg"
        var downloaded: URI? = null
        val service = StandaloneArtworkSearchService(
            imageDownloader = { uri -> downloaded = uri; byteArrayOf(7, 8, 9) },
            searchProvider = { listOf(ArtworkSearchResult(selectedUrl, "Artist", "Album")) },
        )

        assertTrue(service.downloadSelectedArtwork("Artist", "Album", selectedUrl).contentEquals(byteArrayOf(7, 8, 9)))
        assertEquals(URI(selectedUrl), downloaded)
        assertFailsWith<IllegalArgumentException> {
            service.downloadSelectedArtwork("Artist", "Album", "https://127.0.0.1/private")
        }
        assertFailsWith<IllegalArgumentException> {
            service.downloadSelectedArtwork("Artist", "Album", "https://lastfm.freetls.fastly.net/other.jpg")
        }
    }

    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().use { output ->
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output)
        output.toByteArray()
    }

    private fun encodedImage(format: String): ByteArray = ByteArrayOutputStream().use { output ->
        ImageIO.write(BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), format, output)
        output.toByteArray()
    }
}
