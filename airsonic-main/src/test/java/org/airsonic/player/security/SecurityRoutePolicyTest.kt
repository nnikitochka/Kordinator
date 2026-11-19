package org.airsonic.player.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityRoutePolicyTest {
    @Test
    fun matchesOnlyTheRouteWildcardsUsedByTheSecurityPolicy() {
        assertTrue(SecurityRoutePolicy.matchesPathPattern("/ext/stream/**", "/ext/stream"))
        assertTrue(SecurityRoutePolicy.matchesPathPattern("/ext/stream/**", "/ext/stream/song.mp3"))
        assertFalse(SecurityRoutePolicy.matchesPathPattern("/ext/stream/**", "/ext/streaming/song.mp3"))
        assertTrue(SecurityRoutePolicy.matchesPathPattern("/ext/coverArt*", "/ext/coverArt.view"))
        assertFalse(SecurityRoutePolicy.matchesPathPattern("/ext/coverArt*", "/ext/coverArt/view"))
        assertTrue(SecurityRoutePolicy.matchesPathPattern("/ext/captions**", "/ext/captions/list"))
    }

    @Test
    fun exposesOnlyTheRemainingPublicApiPaths() {
        assertEquals(
            listOf("/rest/recover", "/error"),
            SecurityRoutePolicy.WEB_PUBLIC_PATHS,
        )
    }

    @Test
    fun keepsOnlyTheRemainingRoleRules() {
        assertEquals(
            listOf(SecurityRoutePolicy.RoleRule(listOf("/rest/startScan*"), "ADMIN")),
            SecurityRoutePolicy.WEB_ROLE_RULES,
        )
    }

    @Test
    fun keepsExternalTemporaryAndUserRolePaths() {
        assertEquals(
            listOf("/ext/stream/**", "/ext/coverArt*", "/ext/hls/**", "/ext/captions**"),
            SecurityRoutePolicy.EXT_ROLE_PATHS,
        )
        assertEquals(listOf("TEMP", "USER"), SecurityRoutePolicy.EXT_ROLE_NAMES)
    }
}
