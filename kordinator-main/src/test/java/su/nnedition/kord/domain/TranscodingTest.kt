package su.nnedition.kord.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TranscodingTest {
    @Test
    fun equalityAndHashCodeUseTheDatabaseId() {
        val profile = profile(id = 4)
        val sameId = profile(id = 4, name = "Different profile")
        val otherId = profile(id = 5)

        assertEquals(profile, sameId)
        assertEquals(profile.hashCode(), sameId.hashCode())
        assertNotEquals(profile, otherId)
        assertEquals(profile(id = null), profile(id = null, name = "Another unsaved profile"))
    }

    @Test
    fun sourceFormatsPreserveQuotedGroups() {
        val profile = profile(id = 1, sourceFormats = "ogg wav \"m4a aac\"")

        assertContentEquals(arrayOf("ogg", "wav", "m4a aac"), profile.getSourceFormatsAsArray())
    }

    private fun profile(id: Int?, name: String = "MP3", sourceFormats: String = "ogg wav") =
        Transcoding(id, name, sourceFormats, "mp3", "ffmpeg %s", null, null, true)
}
