package org.airsonic.player.service

import chameleon.playlist.SpecificPlaylist
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.dao.PlaylistDao
import org.airsonic.player.domain.Playlist
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.playlist.PlaylistImportHandler
import org.apache.commons.lang3.tuple.Pair
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.atomic.AtomicReference
import java.nio.file.Files
import java.nio.file.Path

class PlaylistServiceAutoImportTest {
    @TempDir
    lateinit var playlistFolder: Path

    @Test
    fun autoImportPassesSourceFileToPlaylistHandler() {
        val mediaFileDao = mock(MediaFileDao::class.java)
        val playlistDao = mock(PlaylistDao::class.java)
        val securityService = mock(SecurityService::class.java)
        val settingsService = mock(SettingsService::class.java)
        val pathWatcherService = mock(PathWatcherService::class.java)
        val mediaFileService = mock(MediaFileService::class.java)
        val importedLocation = AtomicReference<Path>()
        val handler = object : PlaylistImportHandler(mediaFileService, settingsService) {
            override fun canHandle(playlistClass: Class<out SpecificPlaylist>) = true
            override fun handle(inputSpecificPlaylist: SpecificPlaylist, location: Path): Pair<List<org.airsonic.player.domain.MediaFile>, List<String?>> {
                importedLocation.set(location)
                return Pair.of(emptyList(), emptyList())
            }
        }
        val playlistFile = playlistFolder.resolve("favorites.m3u")
        Files.writeString(playlistFile, "#EXTM3U\n")

        `when`(settingsService.playlistFolder).thenReturn(playlistFolder.toString())
        `when`(playlistDao.getAllPlaylists()).thenReturn(emptyList())
        `when`(playlistDao.getPlaylistFileStats(anyInt())).thenReturn(Pair.of(0, 0.0))
        `when`(playlistDao.getPlaylist(anyInt())).thenAnswer { invocation ->
            Playlist().apply { id = invocation.getArgument(0) }
        }
        val service = PlaylistService(
            mediaFileDao, playlistDao, securityService, settingsService,
            emptyList(), listOf(handler), pathWatcherService,
        )

        service.importPlaylists()

        assertEquals(playlistFile, importedLocation.get())
        verify(playlistDao).setFilesInPlaylist(0, emptyList())
    }
}
