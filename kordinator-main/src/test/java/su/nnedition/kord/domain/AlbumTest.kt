package su.nnedition.kord.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlbumTest {
    @Test
    fun defaultAlbumHasEmptyAtomicCounters() {
        val album = Album()

        assertEquals(0, album.songCount)
        assertEquals(0.0, album.duration)
        assertEquals(0, album.playCount)
        assertFalse(album.isPresent)
        assertNull(album.art)

        album.incrementSongCount()
        album.incrementDuration(2.5)
        album.incrementPlayCount()

        assertEquals(1, album.songCount)
        assertEquals(2.5, album.duration)
        assertEquals(1, album.playCount)
    }

    @Test
    fun fullConstructorPreservesNullableMetadataAndFolder() {
        val created = Instant.parse("2024-01-02T03:04:05Z")
        val album = Album(7, "Artist/Album", "Album", "Artist", 3, 420.5, 2024, "Rock", 9,
            null, null, created, created, true, 2, "release-id")

        assertEquals(7, album.id)
        assertEquals("Artist/Album", album.path)
        assertEquals(3, album.songCount)
        assertEquals(420.5, album.duration)
        assertEquals(9, album.playCount)
        assertEquals(created, album.created)
        assertTrue(album.isPresent)
        assertEquals(2, album.folderId)
        assertEquals("release-id", album.musicBrainzReleaseId)
    }
}
