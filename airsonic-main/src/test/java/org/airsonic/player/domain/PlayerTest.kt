package org.airsonic.player.domain

import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerTest {
    @Test
    fun defaultsAndDescriptionsMatchTheJavaModel() {
        val player = Player()

        assertNull(player.id)
        assertNull(player.name)
        assertEquals(PlayerTechnology.WEB, player.technology)
        assertTrue(player.dynamicIp)
        assertTrue(player.autoControlEnabled)
        assertTrue(player.m3uBomEnabled)
        assertEquals(TranscodeScheme.OFF, player.transcodeScheme)
        assertNull(player.playQueue)
        assertEquals("Player null [null]", player.description)
        assertEquals("Player null", player.shortDescription)

        player.id = 17
        player.username = "listener"
        assertEquals("Player 17 [listener]", player.toString())
        assertEquals("Player 17", player.shortDescription)
    }

    @Test
    fun beanPropertiesAndTechnologyHelpersRemainAvailableToJava() {
        val player = Player()
        val queue = PlayQueue { MusicFolder(java.nio.file.Path.of("."), "test", MusicFolder.Type.MEDIA, true, null) }
        val seen = Instant.parse("2026-11-24T00:00:00Z")

        player.name = "  "
        player.technology = PlayerTechnology.JUKEBOX
        player.clientId = "desktop"
        player.type = "jukebox"
        player.ipAddress = "127.0.0.1"
        player.dynamicIp = false
        player.autoControlEnabled = false
        player.m3uBomEnabled = false
        player.lastSeen = seen
        player.transcodeScheme = TranscodeScheme.MAX_192
        player.playQueue = queue

        assertTrue(player.isJukebox())
        assertFalse(player.isExternal())
        assertFalse(player.isExternalWithPlaylist())
        assertFalse(player.isWeb())
        assertEquals("Player null", player.shortDescription)
        assertEquals(queue, player.playQueue)
        assertEquals(seen, player.lastSeen)

        val playerClass = Player::class.java
        assertEquals(Player::class.java, playerClass.getConstructor().declaringClass)
        assertFalse(Modifier.isFinal(playerClass.getMethod("isJukebox").modifiers))
        assertFalse(Modifier.isFinal(playerClass.getMethod("setPlayQueue", PlayQueue::class.java).modifiers))
    }
}
