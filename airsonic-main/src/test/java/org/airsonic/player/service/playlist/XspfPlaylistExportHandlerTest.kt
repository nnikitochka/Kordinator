package org.airsonic.player.service.playlist

import chameleon.playlist.xspf.Playlist as XspfPlaylist
import chameleon.playlist.xspf.XspfProvider
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.dao.PlaylistDao
import org.airsonic.player.domain.CoverArt.EntityType
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.Playlist
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.media.MediaFolderService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.nio.file.Path

class XspfPlaylistExportHandlerTest {
    @Test
    fun exportsOrderedTracksWithMetadataPathsAndArtwork() {
        val files = mock(MediaFileDao::class.java)
        val playlists = mock(PlaylistDao::class.java)
        val folders = mock(MediaFolderService::class.java)
        val artwork = mock(CoverArtService::class.java)
        val first = MediaFile().apply {
            id = 11; folderId = 1; path = "album/first.mp3"; title = "First"; artist = "Artist"
            albumName = "Album"; trackNumber = 1; duration = 12.6
        }
        val second = MediaFile().apply { id = 12; folderId = 1; path = "album/second.mp3"; title = "Second"; duration = 2.0 }
        `when`(files.getFilesInPlaylist(3)).thenReturn(listOf(first, second))
        `when`(playlists.getPlaylist(3)).thenReturn(Playlist().apply { name = "Favorites"; username = "alice" })
        `when`(folders.getMusicFolderById(1)).thenReturn(MusicFolder(1, Path.of("/music"), "Music", MusicFolder.Type.MEDIA, true, null))
        `when`(artwork.getFullPath(EntityType.MEDIA_FILE, 11)).thenReturn(Path.of("/art/cover.jpg"))
        val handler = XspfPlaylistExportHandler(files, playlists, folders, artwork)
        assertTrue(handler.canHandle(XspfProvider::class.java))
        val result = handler.handle(3, mock(XspfProvider::class.java)) as XspfPlaylist
        assertEquals("Favorites", result.title)
        assertEquals("Airsonic user alice", result.creator)
        assertEquals(listOf("First", "Second"), result.tracks.map { it.title })
        assertEquals(13, result.tracks[0].duration)
        assertEquals("/art/cover.jpg", result.tracks[0].image)
        assertEquals("/music/album/first.mp3", result.tracks[0].stringContainers.single().text)
        assertNull(result.tracks[1].image)
    }
}
