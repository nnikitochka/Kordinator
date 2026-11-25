package su.nnedition.kord.io

import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.domain.PlayQueue
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.function.Function

class PlayQueueInputStreamTest {
    @Test
    fun readsTracksInOrderAndNotifiesWhenEachTrackStartsAndEnds() {
        val first = mediaFile("first")
        val second = mediaFile("second")
        val queue = TestPlayQueue(listOf(first, second))
        val starts = mutableListOf<MediaFile>()
        val ends = mutableListOf<MediaFile>()
        val stream = PlayQueueInputStream(
            queue,
            starts::add,
            ends::add,
            Function<MediaFile, InputStream> { file -> ByteArrayInputStream(file.path!!.toByteArray()) },
        )

        stream.use {
            val bytes = ByteArray(16)
            assertThat(it.read(bytes)).isEqualTo(5)
            assertThat(String(bytes, 0, 5)).isEqualTo("first")
            assertThat(it.read(bytes)).isEqualTo(6)
            assertThat(String(bytes, 0, 6)).isEqualTo("second")
            assertThat(it.read(bytes)).isEqualTo(-1)
        }

        assertThat(starts).containsExactly(first, second)
        assertThat(ends).containsExactly(first, second)
        assertThat(queue.position).isEqualTo(2)
    }

    private fun mediaFile(path: String) = MediaFile().apply {
        this.path = path
        folderId = 1
    }

    private class TestPlayQueue(private val tracks: List<MediaFile>) :
        PlayQueue(Function<Int, MusicFolder> { throw UnsupportedOperationException("Folder lookup is unused") }) {
        var position = 0
            private set

        override fun getCurrentFile(): MediaFile? = tracks.getOrNull(position)

        override fun next() {
            position++
        }
    }
}
