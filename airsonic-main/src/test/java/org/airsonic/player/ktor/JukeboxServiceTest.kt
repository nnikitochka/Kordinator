package org.airsonic.player.ktor

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Player
import org.airsonic.player.service.JukeboxLegacySubsonicService
import org.airsonic.player.service.jukebox.JukeboxQueueTrack
import org.airsonic.player.service.media.MediaFolderService
import org.airsonic.player.service.jukebox.JukeboxService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JukeboxServiceTest {
    @Test
    fun queueIsBearerOwnerScopedAndSupportsQueueCommands() {
        val audio = Mockito.mock(JukeboxLegacySubsonicService::class.java)
        val service = JukeboxService(Mockito.mock(MediaFolderService::class.java), audio)
        val tracks = listOf(
            JukeboxQueueTrack(101, mediaFile(1, "First")),
            JukeboxQueueTrack(202, mediaFile(2, "Second")),
        )

        val initial = service.replaceQueue(7, "listener", tracks)
        assertEquals("PAUSED", initial.status)
        assertEquals(listOf(101L, 202L), initial.tracks.map { it.trackId })
        assertFailsWith<IllegalStateException> { service.state(8) }
        assertFailsWith<IllegalArgumentException> { service.replaceQueue(8, "other", tracks) }

        val paused = service.command(7, "pause", null, null, null)
        assertEquals("PAUSED", paused.status)
        Mockito.verify(audio).updateStandaloneJukebox(Mockito.any(Player::class.java) ?: Player(), Mockito.eq(0))
        val gain = service.command(7, "gain", null, null, 0.4f)
        assertEquals(0.4f, gain.gain)
        Mockito.verify(audio).setGain(0.4f)

        assertTrue(service.release(7))
        assertFalse(service.release(7))
        assertEquals("IDLE", service.state(8).status)
    }

    private fun mediaFile(id: Int, title: String) = MediaFile().apply {
        this.id = id
        this.title = title
        artist = "Artist"
        albumName = "Album"
        folderId = 1
        mediaType = MediaFile.MediaType.MUSIC
        present = true
    }
}
