package org.airsonic.player.service.metadata

import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.service.MediaFolderService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.nio.file.Path

class MetaDataParsingTest {
    @Test
    fun guessesDirectoryMetadataAndParsesTagNumbers() {
        val folder = Path.of("/music")
        val service = mock(MediaFolderService::class.java)
        `when`(service.getAllMusicFolders(false, true)).thenReturn(
            listOf(MusicFolder(1, folder, "Music", MusicFolder.Type.MEDIA, true, null)),
        )
        val parser = DefaultMetaDataParser(service)
        val metadata = parser.getMetaData(folder.resolve("Artist/Artist - Album/01 - Song.mp3"))
        assertEquals("Artist", metadata.artist)
        assertEquals("Album", metadata.albumName)
        assertEquals("01 - Song", metadata.title)
        assertEquals(4, parser.parseTrackNumber("4/12"))
        assertNull(parser.parseTrackNumber("0/12"))
        assertEquals(2020, parser.parseYear("2020-03-01"))
        assertNull(parser.parseInteger(" 0 "))
        assertNull(parser.parseInteger("2147483648"))
        assertEquals("Song", parser.removeTrackNumberFromTitle(" 01 - Song ", 1))
        assertEquals("01 - Song", parser.removeTrackNumberFromTitle("01 - Song", 2))
    }
}
