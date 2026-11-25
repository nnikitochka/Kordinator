package org.airsonic.player.service.upnp

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Playlist
import org.airsonic.player.service.PlaylistService
import org.fourthline.cling.support.model.container.PlaylistContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PlaylistUpnpProcessorTest {
    @Test
    fun createsPlaylistContainersAndResolvesTracks() {
        val playlist = Playlist().apply { id = 12; name = "Favorites"; comment = "Picked tracks" }
        val tracks = listOf(mock(MediaFile::class.java), mock(MediaFile::class.java))
        val playlistService = mock(PlaylistService::class.java)
        `when`(playlistService.allPlaylists).thenReturn(listOf(playlist))
        `when`(playlistService.getPlaylist(12)).thenReturn(playlist)
        `when`(playlistService.getFilesInPlaylist(12)).thenReturn(tracks)
        val directory = mock(DispatchingContentDirectory::class.java)
        `when`(directory.getPlaylistService()).thenReturn(playlistService)
        val processor = PlaylistUpnpProcessor().apply {
            setPlaylistService(playlistService)
            dispatchingContentDirectory = directory
        }

        val container = processor.createContainer(playlist) as PlaylistContainer

        assertEquals("playlist-12", container.id)
        assertEquals("playlist", container.parentID)
        assertEquals("Favorites", container.title)
        assertEquals("Picked tracks", container.description)
        assertEquals(2, container.childCount)
        assertEquals(listOf(playlist), processor.allItems)
        assertSame(playlist, processor.getItemById("12"))
        assertEquals(tracks, processor.getChildren(playlist))
        verify(playlistService, org.mockito.Mockito.times(2)).getFilesInPlaylist(12)
    }
}
