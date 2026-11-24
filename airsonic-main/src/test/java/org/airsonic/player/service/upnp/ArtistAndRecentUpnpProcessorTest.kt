package org.airsonic.player.service.upnp

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.domain.Album
import org.airsonic.player.domain.Artist
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.service.MediaFolderService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.nio.file.Path

class ArtistAndRecentUpnpProcessorTest {
    @Test
    fun sortsArtistsAndAddsAllAlbumsEntryWhenNeeded() {
        val folders = listOf(MusicFolder(1, Path.of("/music"), "Music", MusicFolder.Type.MEDIA, true, null))
        val folderService = mock(MediaFolderService::class.java)
        `when`(folderService.allMusicFolders).thenReturn(folders)
        val directory = mock(DispatchingContentDirectory::class.java)
        `when`(directory.mediaFolderService).thenReturn(folderService)
        val artistDao = mock(ArtistDao::class.java)
        val artist = Artist(id = 5, name = "The Artist", albumCount = 2)
        `when`(artistDao.getAlphabetialArtists(0, Int.MAX_VALUE, folders)).thenReturn(listOf(
            Artist(id = 1, name = "Zebra"), Artist(id = 2, name = "!alpha"), artist,
        ))
        val albumDao = mock(AlbumDao::class.java)
        `when`(albumDao.getAlbumsForArtist("The Artist", folders)).thenReturn(listOf(Album().apply { id = 1 }, Album().apply { id = 2 }))
        val albumProcessor = mock(AlbumUpnpProcessor::class.java)
        `when`(albumProcessor.getAlbumDao()).thenReturn(albumDao)
        `when`(directory.albumProcessor).thenReturn(albumProcessor)
        val processor = ArtistUpnpProcessor().apply { setArtistDao(artistDao); dispatchingContentDirectory = directory }
        assertEquals(listOf("!alpha", "The Artist", "Zebra"), processor.getAllItems().map { it.name })
        val children = processor.getChildren(artist)
        assertEquals(listOf(-1, 1, 2), children.map { it.id })
        assertEquals("allByArtist_5", children.first().comment)
        assertEquals(2, processor.createContainer(artist).childCount)
    }

    @Test
    fun recentAlbumsIncludeAggregateAndCapTotalCount() {
        val folders = listOf(MusicFolder(1, Path.of("/music"), "Music", MusicFolder.Type.MEDIA, true, null))
        val folderService = mock(MediaFolderService::class.java)
        `when`(folderService.allMusicFolders).thenReturn(folders)
        val directory = mock(DispatchingContentDirectory::class.java)
        `when`(directory.mediaFolderService).thenReturn(folderService)
        val albums = mock(AlbumDao::class.java)
        `when`(albums.getNewestAlbums(0, 50, folders)).thenReturn(listOf(Album().apply { id = 7 }, Album().apply { id = 8 }))
        `when`(albums.getAlbumCount(folders)).thenReturn(80)
        val processor = RecentAlbumUpnpProcessor().apply { setAlbumDao(albums); dispatchingContentDirectory = directory }
        assertEquals(listOf(-1, 7, 8), processor.getAllItems().map { it.id })
        assertEquals("allRecent", processor.getAllItems().first().comment)
        assertEquals(50, processor.getAllItemsSize())
    }
}
