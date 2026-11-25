package org.airsonic.player.service.upnp

import org.airsonic.player.domain.MediaLibraryStatistics
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.Playlist
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.search.IndexManager
import org.fourthline.cling.support.model.BrowseFlag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.nio.file.Path

class FolderBasedContentDirectoryTest {
    @Test
    fun rootMetadataIncludesLibraryStatisticsAndPlaylistRoot() {
        val statistics = MediaLibraryStatistics().apply { setTotalLengthInBytes(1234) }
        val indexManager = mock(IndexManager::class.java)
        `when`(indexManager.getStatistics()).thenReturn(statistics)
        val folders = listOf(MusicFolder(1, Path.of("/music"), "Music", MusicFolder.Type.MEDIA, true, null))
        val mediaFolderService = mock(MediaFolderService::class.java)
        `when`(mediaFolderService.allMusicFolders).thenReturn(folders)
        val directory = FolderBasedContentDirectory(null, mediaFolderService, null, indexManager)

        val result = directory.browse("0", BrowseFlag.METADATA, null, 0, 0, null)

        assertEquals(1L, result.countLong)
        assertEquals(1L, result.totalMatchesLong)
        assertTrue(result.result.contains("childCount=\"2\""))
        verify(indexManager).getStatistics()
    }

    @Test
    fun playlistRootPaginatesAndUsesFolderBasedIds() {
        val playlist = Playlist().apply { id = 5; name = "Favorites"; comment = "Saved tracks" }
        val playlistService = mock(PlaylistService::class.java)
        `when`(playlistService.allPlaylists).thenReturn(listOf(playlist))
        `when`(playlistService.getFilesInPlaylist(5)).thenReturn(emptyList())
        val directory = FolderBasedContentDirectory(null, null, playlistService, null)

        val result = directory.browse("playlists", BrowseFlag.DIRECT_CHILDREN, null, 0, 0, null)

        assertEquals(1L, result.countLong)
        assertEquals(1L, result.totalMatchesLong)
        assertTrue(result.result.contains("id=\"playlist-5\""))
        assertTrue(result.result.contains("Favorites"))
    }
}
