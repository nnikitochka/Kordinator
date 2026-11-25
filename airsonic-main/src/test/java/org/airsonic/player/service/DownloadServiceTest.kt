package org.airsonic.player.service

import com.google.common.util.concurrent.RateLimiter
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.TransferStatus
import org.airsonic.player.domain.User
import org.airsonic.player.security.CallerContext
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.media.MediaFolderService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.zip.ZipInputStream

class DownloadServiceTest {
    @Test
    fun `single file response streams file contents`(@TempDir root: Path) {
        val bytes = "music bytes".toByteArray()
        Files.write(root.resolve("song.mp3"), bytes)
        val folder = musicFolder(root)
        val file = mediaFile("song.mp3", MediaFile.MediaType.MUSIC, bytes.size.toLong())
        val fixture = fixture(folder, file)

        val response = fixture.service.prepare(
            fixture.user, fixture.context, fixture.player, Optional.of(10), null, null, null, null
        )!!

        response.openStream().use { assertEquals(bytes.toList(), it.readBytes().toList()) }
    }

    @Test
    fun `selected folder children are written to a stored zip`(@TempDir root: Path) {
        val folderPath = Files.createDirectory(root.resolve("album"))
        Files.writeString(folderPath.resolve("one.mp3"), "one")
        Files.writeString(folderPath.resolve("two.mp3"), "two")
        val musicFolder = musicFolder(root)
        val directory = mediaFile("album", MediaFile.MediaType.DIRECTORY, null)
        val first = mediaFile("album/one.mp3", MediaFile.MediaType.MUSIC, 3)
        val second = mediaFile("album/two.mp3", MediaFile.MediaType.MUSIC, 3)
        val fixture = fixture(musicFolder, directory, listOf(first, second))

        val response = fixture.service.prepare(
            fixture.user, fixture.context, fixture.player, Optional.of(10), null, null, listOf(0, 1), null
        )!!
        val entries = response.openStream().use { stream ->
            ZipInputStream(stream).use { zip ->
                buildMap {
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        put(entry.name, zip.readBytes().decodeToString())
                    }
                }
            }
        }

        assertEquals(mapOf("one.mp3" to "one", "two.mp3" to "two"), entries)
    }

    private fun fixture(
        musicFolder: MusicFolder,
        selectedFile: MediaFile,
        children: List<MediaFile> = emptyList()
    ): Fixture {
        val player = Player().apply { id = 7; name = "test" }
        val user = User("tester", null)
        val context = CallerContext("tester", emptySet(), setOf(1), true)
        val status = TransferStatus(player)
        val statusService = Mockito.mock(StatusService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
        val playlistService = Mockito.mock(PlaylistService::class.java)
        val settingsService = Mockito.mock(SettingsService::class.java)
        val mediaFileService = Mockito.mock(MediaFileService::class.java)
        val mediaFolderService = Mockito.mock(MediaFolderService::class.java)
        val coverArtService = Mockito.mock(CoverArtService::class.java)
        `when`(statusService.createDownloadStatus(player)).thenReturn(status)
        `when`(securityService.isFolderAccessAllowed(selectedFile, context)).thenReturn(true)
        `when`(settingsService.downloadBitrateLimiter).thenReturn(RateLimiter.create(1_000_000.0))
        `when`(mediaFileService.getMediaFile(10)).thenReturn(selectedFile)
        `when`(mediaFileService.getChildrenOf(selectedFile, true, false, true)).thenReturn(children)
        `when`(mediaFolderService.getMusicFolderById(1)).thenReturn(musicFolder)
        val service = DownloadService(
            statusService, securityService, playlistService, settingsService,
            mediaFileService, mediaFolderService, coverArtService
        )
        return Fixture(service, user, context, player)
    }

    private fun musicFolder(root: Path) = MusicFolder(
        1, root, "library", MusicFolder.Type.MEDIA, true, null
    )

    private fun mediaFile(path: String, type: MediaFile.MediaType, size: Long?) = MediaFile().apply {
        id = 10
        this.path = path
        folderId = 1
        mediaType = type
        fileSize = size
    }

    private data class Fixture(
        val service: DownloadService,
        val user: User,
        val context: CallerContext,
        val player: Player
    )
}
