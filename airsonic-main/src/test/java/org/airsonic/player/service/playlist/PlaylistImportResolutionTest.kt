package org.airsonic.player.service.playlist

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.SettingsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.nio.file.Path

class PlaylistImportResolutionTest {
    @Test
    fun relativePathsSearchPlaylistFolderLibrariesAndWorkingDirectoryInOrder() {
        val files = mock(MediaFileService::class.java)
        val settings = mock(SettingsService::class.java)
        val folder = Path.of("/playlists")
        val relative = Path.of("album/track.mp3")
        val playlistMatch = MediaFile()
        val libraryMatch = MediaFile().apply { mediaType = MediaFile.MediaType.MUSIC }
        val directoryMatch = MediaFile().apply { mediaType = MediaFile.MediaType.DIRECTORY }
        val workingMatch = MediaFile()
        `when`(settings.playlistFolder).thenReturn(folder.toString())
        `when`(files.getMediaFile(folder.resolve(relative))).thenReturn(playlistMatch)
        `when`(files.getMediaFilesByRelativePath(relative)).thenReturn(listOf(libraryMatch, directoryMatch))
        `when`(files.getMediaFile(Path.of(".").toAbsolutePath().resolve(relative).normalize())).thenReturn(workingMatch)
        val handler = object : DefaultPlaylistImportHandler(files, settings) {
            fun resolve(path: String?): List<MediaFile> = getMediaFiles(path)
        }
        assertEquals(listOf(playlistMatch, libraryMatch, workingMatch), handler.resolve(relative.toString()))
        assertTrue(handler.resolve(" ").isEmpty())
        val absolute = Path.of("/outside/song.mp3")
        `when`(files.getMediaFile(absolute)).thenReturn(libraryMatch)
        assertEquals(listOf(libraryMatch), handler.resolve(absolute.toString()))
    }
}
