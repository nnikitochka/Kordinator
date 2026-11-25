package su.nnedition.kord.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MediaFileTest {
    @Test
    fun beanApiAndConstructorPreserveMetadata() {
        val changed = Instant.parse("2026-11-24T00:00:00Z")
        val file = MediaFile(
            8, "music/track.flac", 3, MediaFile.MediaType.MUSIC, "flac", "Track", "Album", "Artist",
            "Album Artist", 2, 7, 2024, "Jazz", 900, true, 210.5, 12_345, null, null,
            "music", 4, changed, "comment", changed, changed, changed, changed, true, 9, "release", "recording"
        )

        assertEquals("Track", file.name)
        assertEquals("Track", MediaFile::class.java.getMethod("getName").invoke(file))
        assertEquals("music/track.flac", file.path)
        assertEquals("music/track.flac", file.relativePath.toString())
        assertEquals(2, file.discNumber)
        assertEquals(7, file.trackNumber)
        assertTrue(file.isAudio)
        assertTrue(file.isPresent)
        assertTrue(file.isVariableBitRate)
        assertEquals(9, MediaFile::class.java.getMethod("getVersion").invoke(file))

        val javaApi = MediaFile::class.java
        assertTrue(javaApi.getMethod("isPresent").returnType == Boolean::class.javaPrimitiveType)
        assertTrue(javaApi.getMethod("setPresent", Boolean::class.javaPrimitiveType) != null)
        assertTrue(javaApi.getMethod("isVariableBitRate").returnType == Boolean::class.javaPrimitiveType)
        assertTrue(javaApi.getMethod("getMusicBrainzRecordingId").returnType == String::class.java)
    }

    @Test
    fun equalityAndIdHelpersKeepPathAndFolderSemantics() {
        val first = MediaFile().apply {
            path = "music/track.flac"
            folderId = 3
            id = 10
        }
        val same = MediaFile().apply {
            path = "music/track.flac"
            folderId = 3
            id = 11
        }
        val otherFolder = MediaFile().apply {
            path = "music/track.flac"
            folderId = 4
            id = 10
        }

        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertNotEquals(first, otherFolder)
        assertEquals(listOf(10, 11), MediaFile.toIdList(listOf(first, same)))
        assertEquals(10, MediaFile.toId().apply(first))
        assertFalse(first.isDirectory)
    }
}
