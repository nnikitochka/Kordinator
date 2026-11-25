package org.airsonic.player.service.upnp

import org.fourthline.cling.support.contentdirectory.ContentDirectoryErrorCode
import org.fourthline.cling.support.contentdirectory.ContentDirectoryException
import org.fourthline.cling.support.model.BrowseFlag
import org.fourthline.cling.support.model.BrowseResult
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class DispatchingContentDirectoryTest {
    @Test
    fun dispatchesKnownRootAndExpandsZeroMaxResults() {
        val genreProcessor = mock(GenreUpnpProcessor::class.java)
        val result = BrowseResult("genre result", 1, 1)
        `when`(genreProcessor.browseRoot(null, 4, Long.MAX_VALUE, null)).thenReturn(result)
        val directory = DispatchingContentDirectory(
            null, null, null, null, null, genreProcessor, null,
            null, null, null, null, null,
        )

        assertSame(result, directory.browse("genre", BrowseFlag.DIRECT_CHILDREN, null, 4, 0, null))
    }

    @Test
    fun treatsUnknownRootAsMediaFileId() {
        val mediaFileProcessor = mock(MediaFileUpnpProcessor::class.java)
        val result = BrowseResult("track result", 1, 1)
        `when`(mediaFileProcessor.browseObject("37", null, 0, 10, null)).thenReturn(result)
        val directory = DispatchingContentDirectory(
            mediaFileProcessor, null, null, null, null, null, null,
            null, null, null, null, null,
        )

        assertSame(result, directory.browse("37", BrowseFlag.DIRECT_CHILDREN, null, 0, 10, null))
    }

    @Test
    fun wrapsProcessorFailuresAsCannotProcess() {
        val genreProcessor = mock(GenreUpnpProcessor::class.java)
        `when`(genreProcessor.browseRoot(null, 0, 10, null)).thenThrow(IllegalStateException("broken processor"))
        val directory = DispatchingContentDirectory(
            null, null, null, null, null, genreProcessor, null,
            null, null, null, null, null,
        )

        val failure = assertThrows(ContentDirectoryException::class.java) {
            directory.browse("genre", BrowseFlag.DIRECT_CHILDREN, null, 0, 10, null)
        }

        org.junit.jupiter.api.Assertions.assertEquals(ContentDirectoryErrorCode.CANNOT_PROCESS.code, failure.errorCode)
    }

    @Test
    fun routesArtistSearchUsingClassAndTitleCriteria() {
        val artistProcessor = mock(ArtistUpnpProcessor::class.java)
        val result = BrowseResult("artist search", 1, 1)
        `when`(artistProcessor.searchByName("Miles", 3, 7, null)).thenReturn(result)
        val directory = DispatchingContentDirectory(
            null, null, null, null, artistProcessor, null, null,
            null, null, null, null, null,
        )

        val response = directory.search(
            "0",
            "upnp:class derivedfrom \"object.container.person.musicArtist\" and dc:title contains \"Miles\"",
            "*",
            3,
            7,
            null,
        )

        assertSame(result, response)
    }
}
