package org.airsonic.player.service.metadata

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.service.MediaFolderService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import java.nio.file.Files
import java.nio.file.Path

class JaudiotaggerParserTest {
    @TempDir lateinit var directory: Path

    @Test
    fun writesReadsAndClearsTagsInRealMp3() {
        val source = Path.of("src/test/resources/MEDIAS/Music/_DIR_ Ravel/_DIR_ Ravel - Complete Piano Works/01 - Gaspard de la Nuit - i. Ondine.mp3")
        val copy = Files.copy(source, directory.resolve("track.MP3"))
        val folders = mock(MediaFolderService::class.java)
        `when`(folders.getMusicFolderById(7)).thenReturn(MusicFolder(7, directory, "Test", MusicFolder.Type.MEDIA, true, null))
        val parser = JaudiotaggerParser(folders)
        val file = MediaFile().apply { path = "track.MP3"; folderId = 7 }
        val tags = MetaData().apply {
            artist = " Artist "; albumArtist = " Album Artist "; albumName = " Album "
            title = " Song "; genre = " Rock "; trackNumber = 7; year = 2020
        }
        assertTrue(parser.isApplicable(copy))
        parser.setMetaData(file, tags)
        val read = parser.getRawMetaData(copy)
        assertEquals("Artist", read.artist)
        assertEquals("Album Artist", read.albumArtist)
        assertEquals("Album", read.albumName)
        assertEquals("Song", read.title)
        assertEquals("Rock", read.genre)
        assertEquals(7, read.trackNumber)
        assertEquals(2020, read.year)
        tags.trackNumber = null
        tags.year = null
        parser.setMetaData(file, tags)
        val cleared = parser.getRawMetaData(copy)
        assertNull(cleared.trackNumber)
        assertNull(cleared.year)
        assertTrue(parser.isEditingSupported())
        assertEquals(0, parser.getOrder())
    }

    @Test
    fun missingOrInvalidFilesAreRejectedOrHandled() {
        val parser = JaudiotaggerParser(mock(MediaFolderService::class.java))
        val missing = directory.resolve("missing.mp3")
        assertFalse(parser.isApplicable(missing))
        assertFalse(JaudiotaggerParser.isImageAvailable(missing))
        assertNull(parser.getRawMetaData(missing).title)
        val unsupported = Files.createFile(directory.resolve("sample.txt"))
        assertFalse(parser.isApplicable(unsupported))
    }
}
