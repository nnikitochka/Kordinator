package su.nnedition.kord.domain

import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.time.Instant
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserSettingsTest {
    @Test
    fun constructorsAndPreferenceDefaultsMatchTheJavaModel() {
        val empty = UserSettings()
        val named = UserSettings("listener")

        assertNull(empty.username)
        assertEquals("listener", named.username)
        assertEquals(AlbumListType.RANDOM, empty.defaultAlbumList)
        assertEquals(TranscodeScheme.OFF, empty.transcodeScheme)
        assertEquals(AvatarScheme.NONE, empty.avatarScheme)
        assertEquals(-1, empty.selectedMusicFolderId)
        assertEquals(10, empty.paginationSizeFiles)
        assertEquals(7, empty.paginationSizeFolders)
        assertEquals(10, empty.paginationSizePlaylist)
        assertEquals(10, empty.paginationSizePlayqueue)
        assertEquals(20, empty.paginationSizeBookmarks)
        assertTrue(empty.autoBookmark)
        assertEquals(40, empty.videoBookmarkFrequency)
        assertEquals(10, empty.audioBookmarkFrequency)
        assertEquals(25, empty.searchCount)
        assertTrue(empty.changed != null)

        assertNotSame(empty.mainVisibility, empty.playlistVisibility)
        assertNotSame(empty.playlistVisibility, empty.playqueueVisibility)
        assertNotSame(empty.mainVisibility, named.mainVisibility)
    }

    @Test
    fun visibilityDefaultsAndJavaBeanShapeArePreserved() {
        val visibility = UserSettings.Visibility()
        assertTrue(visibility.artistVisible)
        assertTrue(visibility.albumVisible)
        assertTrue(visibility.durationVisible)
        assertFalse(visibility.discNumberVisible)
        assertFalse(visibility.lastScannedVisible)

        val settings = UserSettings()
        settings.locale = Locale.US
        settings.changed = Instant.EPOCH
        settings.showNowPlayingEnabled = true
        visibility.discNumberVisible = true
        settings.mainVisibility = visibility

        assertEquals(Locale.US, settings.locale)
        assertEquals(Instant.EPOCH, settings.changed)
        assertTrue(settings.showNowPlayingEnabled)
        assertTrue(settings.mainVisibility.discNumberVisible)

        val userSettingsClass = UserSettings::class.java
        assertEquals(userSettingsClass, userSettingsClass.getConstructor().declaringClass)
        assertEquals(userSettingsClass, userSettingsClass.getConstructor(String::class.java).declaringClass)
        assertFalse(Modifier.isFinal(userSettingsClass.getMethod("getShowNowPlayingEnabled").modifiers))
        assertFalse(Modifier.isFinal(userSettingsClass.getMethod("setShowNowPlayingEnabled", Boolean::class.javaPrimitiveType).modifiers))

        val visibilityClass = UserSettings.Visibility::class.java
        assertTrue(Modifier.isStatic(visibilityClass.modifiers))
        assertFalse(Modifier.isFinal(visibilityClass.getMethod("setDiscNumberVisible", Boolean::class.javaPrimitiveType).modifiers))
    }
}
