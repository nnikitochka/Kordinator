package su.nnedition.kord.service.upnp

import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.domain.Genre
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import org.fourthline.cling.support.model.container.GenreContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.nio.file.Path

class GenreUpnpProcessorTest {
    @Test
    fun browsesGenresUsingStableListIndexes() {
        val genres = listOf(Genre("Ambient", 1, 1), Genre("Rock", 5, 2), Genre("Soul", 3, 1))
        val mediaFileService = mock(MediaFileService::class.java)
        `when`(mediaFileService.getGenres(false)).thenReturn(genres)
        val directory = mock(DispatchingContentDirectory::class.java)
        `when`(directory.getMediaFileService()).thenReturn(mediaFileService)
        val processor = GenreUpnpProcessor().apply { setDispatchingContentDirectory(directory) }

        val container = processor.createContainer(genres[1], 1) as GenreContainer
        val page = processor.browseRoot(null, 1, 1, null)

        assertEquals("genre-1", container.id)
        assertEquals("genre", container.parentID)
        assertEquals("Rock", container.title)
        assertEquals(2, container.childCount)
        assertEquals(1L, page.countLong)
        assertEquals(3L, page.totalMatchesLong)
        org.junit.jupiter.api.Assertions.assertTrue(page.result.contains("genre-1"))
        verify(mediaFileService).getGenres(false)
    }

    @Test
    fun resolvesGenresByIndexAndLoadsSongsFromAllLibraries() {
        val genre = Genre("Rock", 5, 2)
        val genres = listOf(genre)
        val mediaFileService = mock(MediaFileService::class.java)
        `when`(mediaFileService.getGenres(false)).thenReturn(genres)
        val folders = listOf(MusicFolder(1, Path.of("/music"), "Music", MusicFolder.Type.MEDIA, true, null))
        val folderService = mock(MediaFolderService::class.java)
        `when`(folderService.getAllMusicFolders()).thenReturn(folders)
        val expectedTracks = listOf(mock(MediaFile::class.java))
        val mediaFileDao = mock(MediaFileDao::class.java)
        `when`(mediaFileDao.getSongsByGenre("Rock", 0, Int.MAX_VALUE, folders)).thenReturn(expectedTracks)
        val mediaProcessor = mock(MediaFileUpnpProcessor::class.java)
        `when`(mediaProcessor.getMediaFileDao()).thenReturn(mediaFileDao)
        val directory = mock(DispatchingContentDirectory::class.java)
        `when`(directory.getMediaFileService()).thenReturn(mediaFileService)
        `when`(directory.getMediaFolderService()).thenReturn(folderService)
        `when`(directory.getMediaFileProcessor()).thenReturn(mediaProcessor)
        val processor = GenreUpnpProcessor().apply { setDispatchingContentDirectory(directory) }

        assertSame(genre, processor.getItemById("0"))
        assertNull(processor.getItemById("1"))
        assertEquals(expectedTracks, processor.getChildren(genre))
        verify(mediaFileDao).getSongsByGenre("Rock", 0, Int.MAX_VALUE, folders)
    }
}
