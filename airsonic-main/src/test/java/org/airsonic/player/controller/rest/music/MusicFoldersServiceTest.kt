package org.airsonic.player.controller.rest.music

import org.airsonic.player.controller.rest.music.folder.MusicFolderLookup
import org.airsonic.player.controller.rest.music.folder.MusicFoldersService
import org.airsonic.player.domain.MusicFolder
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals

class MusicFoldersServiceTest {
    @Test
    fun `returns the current user's folders with their API ids and names`() {
        val folders = listOf(
            MusicFolder(7, Path.of("/music/first"), "First", MusicFolder.Type.MEDIA, true, Instant.EPOCH),
            MusicFolder(9, Path.of("/music/second"), "Second", MusicFolder.Type.MEDIA, true, Instant.EPOCH),
        )
        var requestedUsername: String? = null
        val musicFolderLookup = MusicFolderLookup { username ->
            requestedUsername = username
            folders
        }

        val result = MusicFoldersService(musicFolderLookup).getMusicFolders("alice")

        assertEquals(listOf(7, 9), result.musicFolder.map { it.id })
        assertEquals(listOf("First", "Second"), result.musicFolder.map { it.name })
        assertEquals("alice", requestedUsername)
    }
}
