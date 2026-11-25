package su.nnedition.kord.domain

import org.junit.jupiter.api.Test
import java.io.ObjectStreamClass
import java.lang.reflect.Modifier
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class MusicFolderTest {
    @Test
    fun constructorsAndHelperMethodsKeepTheirDataShape() {
        val first = MusicFolder(1, Path.of("/music/one"), "One", MusicFolder.Type.MEDIA, true, Instant.EPOCH)
        val unsaved = MusicFolder(Path.of("/music/new"), "New", MusicFolder.Type.MEDIA, false, null)

        assertEquals(listOf(1, null), MusicFolder.toIdList(listOf(first, unsaved)))
        assertEquals(listOf("/music/one", "/music/new"), MusicFolder.toPathList(listOf(first, unsaved)))
        assertEquals(1, MusicFolder.toId().apply(first))
        assertEquals("/music/new", MusicFolder.toPath().apply(unsaved))
        assertNull(unsaved.id)
        assertEquals(false, unsaved.isEnabled())
    }

    @Test
    fun equalityAndSerializedIdentifierMatchThePreviousModel() {
        val first = MusicFolder(5, Path.of("/music/one"), "One", MusicFolder.Type.MEDIA, true, null)
        val sameId = MusicFolder(5, Path.of("/music/other"), "Other", MusicFolder.Type.MEDIA, false, null)
        val otherId = MusicFolder(6, Path.of("/music/one"), "One", MusicFolder.Type.MEDIA, true, null)

        assertEquals(first, sameId)
        assertNotEquals(first, otherId)
        assertEquals(-6427012828888540476L, ObjectStreamClass.lookup(MusicFolder::class.java).serialVersionUID)
        assertEquals("enabled", MusicFolder::class.java.getDeclaredField("enabled").name)
        assertEquals(false, Modifier.isFinal(MusicFolder::class.java.getMethod("isEnabled").modifiers))
        assertEquals(false, Modifier.isFinal(MusicFolder::class.java.getMethod("setEnabled", Boolean::class.javaPrimitiveType).modifiers))
    }
}
