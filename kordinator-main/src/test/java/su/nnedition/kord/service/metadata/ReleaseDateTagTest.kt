package su.nnedition.kord.service.metadata

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import su.nnedition.kord.model.ReleaseDate
import su.nnedition.kord.service.media.MediaFolderService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ReleaseDateTagTest {
    @TempDir lateinit var home: Path

    @Test fun readsAndWritesDatesWithoutLosingPrecisionAcrossAudioFormats() {
        for (extension in listOf("mp3", "flac", "ogg", "m4a")) {
            val source = Files.walk(Path.of("src/test/resources/MEDIAS")).use { paths ->
                paths.filter { it.toString().endsWith(".$extension") }.findFirst().orElseThrow()
            }
            for (date in listOf("2026", "2026-02", "2026-02-27")) {
                val file = home.resolve("${date}.$extension")
                Files.copy(source, file)
                val audio = AudioFileIO.read(file.toFile())
                if (audio is MP3File) audio.setID3v2Tag(ID3v24Tag())
                audio.tagOrCreateAndSetDefault.setField(FieldKey.YEAR, date.replace('-', '.'))
                audio.commit()
                val parser = JaudiotaggerParser(Mockito.mock(MediaFolderService::class.java))
                val metadata = parser.getRawMetaData(file)
                assertEquals(ReleaseDate.parse(date), metadata.releaseDate, "$extension read")
                val originalYear = AudioFileIO.read(file.toFile()).tag.getAll(FieldKey.YEAR)
                metadata.title = "Updated title"
                VerifiedTagWriter.write(file, metadata)
                assertEquals(originalYear, AudioFileIO.read(file.toFile()).tag.getAll(FieldKey.YEAR), "$extension preserve")
                metadata.updateReleaseDate = true
                metadata.releaseDate = ReleaseDate(2024, 2, 29)
                VerifiedTagWriter.write(file, metadata)
                assertEquals("2024-02-29", AudioFileIO.read(file.toFile()).tag.getFirst(FieldKey.YEAR))
                metadata.releaseDate = null
                VerifiedTagWriter.write(file, metadata)
                assertTrue(AudioFileIO.read(file.toFile()).tag.getAll(FieldKey.YEAR).all(String::isBlank))
            }
        }
    }

    @Test fun refusesLossyWritesWithoutTouchingTheOriginal() {
        val file = home.resolve("old.mp3")
        Files.copy(Path.of("src/test/resources/MEDIAS/piano.mp3"), file)
        val audio = AudioFileIO.read(file.toFile()) as MP3File
        audio.setID3v2Tag(org.jaudiotagger.tag.id3.ID3v23Tag())
        audio.tagOrCreateAndSetDefault.setField(FieldKey.YEAR, "2020")
        audio.commit()
        val before = Files.readAllBytes(file)
        val metadata = MetaData().apply {
            title = "New title"
            releaseDate = ReleaseDate(2026, 2)
            updateReleaseDate = true
        }
        assertFailsWith<IllegalStateException> { VerifiedTagWriter.write(file, metadata) }
        assertContentEquals(before, Files.readAllBytes(file))
    }
}
