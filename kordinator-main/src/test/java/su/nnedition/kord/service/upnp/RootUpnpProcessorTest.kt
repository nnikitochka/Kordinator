package su.nnedition.kord.service.upnp

import su.nnedition.kord.service.search.IndexManager
import org.fourthline.cling.support.model.WriteStatus
import org.fourthline.cling.support.model.container.StorageFolder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class RootUpnpProcessorTest {
    @Test
    fun exposesSixCategoriesAndRootProperties() {
        val index = mock(IndexManager::class.java)
        val directory = mock(DispatchingContentDirectory::class.java)
        val album = mock(AlbumUpnpProcessor::class.java)
        val artist = mock(ArtistUpnpProcessor::class.java)
        val media = mock(MediaFileUpnpProcessor::class.java)
        val genre = mock(GenreUpnpProcessor::class.java)
        val playlist = mock(PlaylistUpnpProcessor::class.java)
        val recent = mock(RecentAlbumUpnpProcessor::class.java)
        `when`(directory.getAlbumProcessor()).thenReturn(album)
        `when`(directory.getArtistProcessor()).thenReturn(artist)
        `when`(directory.getMediaFileProcessor()).thenReturn(media)
        `when`(directory.getGenreProcessor()).thenReturn(genre)
        `when`(directory.getPlaylistProcessor()).thenReturn(playlist)
        `when`(directory.getRecentAlbumProcessor()).thenReturn(recent)
        val categories = (0..5).map { StorageFolder().apply { id = it.toString() } }
        `when`(album.createRootContainer()).thenReturn(categories[0])
        `when`(artist.createRootContainer()).thenReturn(categories[1])
        `when`(media.createRootContainer()).thenReturn(categories[2])
        `when`(genre.createRootContainer()).thenReturn(categories[3])
        `when`(playlist.createRootContainer()).thenReturn(categories[4])
        `when`(recent.createRootContainer()).thenReturn(categories[5])
        val processor = RootUpnpProcessor(index)
        processor.setDispatchingContentDirectory(directory)
        val root = processor.createRootContainer() as StorageFolder
        assertEquals(DispatchingContentDirectory.CONTAINER_ID_ROOT, root.id)
        assertEquals("-1", root.parentID)
        assertEquals(6, root.childCount)
        assertEquals(-1L, root.storageUsed)
        assertEquals(WriteStatus.NOT_WRITABLE, root.writeStatus)
        assertTrue(root.isSearchable)
        assertEquals(categories, processor.getAllItems())
        verify(index).getStatistics()
    }
}
