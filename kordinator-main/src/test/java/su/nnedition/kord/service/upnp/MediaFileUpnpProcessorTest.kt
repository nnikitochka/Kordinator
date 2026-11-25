package su.nnedition.kord.service.upnp

import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.service.media.MediaFileService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class MediaFileUpnpProcessorTest {
    @Test
    fun sortsChildrenByNormalizedPath() {
        val parent = MediaFile().apply { path = "" }
        val children = mutableListOf(
            MediaFile().apply { path = "folder/Zebra.mp3" },
            MediaFile().apply { path = "folder/a song.mp3" },
            MediaFile().apply { path = "folder/Beta.mp3" },
        )
        val mediaFileService = mock(MediaFileService::class.java)
        `when`(mediaFileService.getChildrenOf(parent, true, true, true)).thenReturn(children)
        val directory = mock(DispatchingContentDirectory::class.java)
        `when`(directory.getMediaFileService()).thenReturn(mediaFileService)
        val processor = MediaFileUpnpProcessor().apply { setDispatchingContentDirectory(directory) }

        val result = processor.getChildren(parent)

        assertEquals(listOf("folder/a song.mp3", "folder/Beta.mp3", "folder/Zebra.mp3"), result.map { it.path })
    }
}
