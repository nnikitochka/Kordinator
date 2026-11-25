package org.airsonic.player.service.upnp

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.MediaFileService
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
