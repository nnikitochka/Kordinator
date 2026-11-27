package su.nnedition.kord.service.metadata

import su.nnedition.kord.model.ReleaseDate

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Commits tags to a sibling copy and verifies YEAR before replacing the original. */
internal object VerifiedTagWriter {
    fun write(path: Path, metadata: MetaData) {
        val target = path.toRealPath()
        val temporary = Files.createTempFile(target.parent, ".kordinator-tags-", ".${target.fileName.toString().substringAfterLast('.')}")
        try {
            Files.copy(target, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
            val audio = AudioFileIO.read(temporary.toFile())
            val tag = audio.tagOrCreateAndSetDefault
            val originalDates = tag.getAll(FieldKey.YEAR).toList()
            tag.setField(FieldKey.ARTIST, metadata.artist.orEmpty())
            tag.setField(FieldKey.ALBUM, metadata.albumName.orEmpty())
            tag.setField(FieldKey.TITLE, metadata.title.orEmpty())
            tag.setField(FieldKey.GENRE, metadata.genre.orEmpty())
            try {
                tag.setField(FieldKey.ALBUM_ARTIST, metadata.albumArtist.orEmpty())
            } catch (_: UnsupportedOperationException) {
                // A legacy tag may not support album artist.
            } catch (_: org.jaudiotagger.tag.KeyNotFoundException) {
                // ID3v1 has no album artist field.
            }
            metadata.trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) } ?: tag.deleteField(FieldKey.TRACK)
            if (metadata.updateReleaseDate) {
                metadata.releaseDate?.let { tag.setField(FieldKey.YEAR, it.toString()) } ?: tag.deleteField(FieldKey.YEAR)
            }
            audio.commit()
            val actual = AudioFileIO.read(temporary.toFile()).tag?.getAll(FieldKey.YEAR).orEmpty()
            if (metadata.updateReleaseDate) {
                val expected = metadata.releaseDate
                check(if (expected == null) actual.all(String::isBlank)
                    else actual.size == 1 && ReleaseDate.parse(actual.single()) == expected) {
                    "The file format cannot preserve the requested release date and its precision"
                }
            } else {
                check(actual == originalDates) { "Tag editing would change the existing YEAR tag" }
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
