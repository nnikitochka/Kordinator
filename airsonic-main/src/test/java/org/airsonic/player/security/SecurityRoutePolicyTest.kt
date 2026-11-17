package org.airsonic.player.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityRoutePolicyTest {
    @Test
    fun keepsTheExistingPublicWebsitePaths() {
        assertEquals(
            listOf("/rest/recover", "/recover*", "/accessDenied*", "/style/**", "/icons/**", "/flash/**", "/script/**", "/login", "/error"),
            SecurityRoutePolicy.WEB_PUBLIC_PATHS,
        )
    }

    @Test
    fun keepsWebsiteRoleRulesAndTheirOrdering() {
        assertEquals(
            listOf(
                SecurityRoutePolicy.RoleRule(
                    listOf("/personalSettings*", "/playerSettings*", "/shareSettings*", "/credentialsSettings*"),
                    "SETTINGS",
                ),
                SecurityRoutePolicy.RoleRule(
                    listOf(
                        "/generalSettings*", "/advancedSettings*", "/userSettings*", "/musicFolderSettings*",
                        "/databaseSettings*", "/transcodeSettings*", "/rest/startScan*",
                    ),
                    "ADMIN",
                ),
                SecurityRoutePolicy.RoleRule(listOf("/deletePlaylist*", "/savePlaylist*"), "PLAYLIST"),
                SecurityRoutePolicy.RoleRule(listOf("/upload*"), "UPLOAD"),
                SecurityRoutePolicy.RoleRule(listOf("/createShare*"), "SHARE"),
                SecurityRoutePolicy.RoleRule(listOf("/changeCoverArt*", "/editTags*"), "COVERART"),
            ),
            SecurityRoutePolicy.WEB_ROLE_RULES,
        )
    }

    @Test
    fun keepsExternalTemporaryAndUserRolePaths() {
        assertEquals(
            listOf("/ext/stream/**", "/ext/coverArt*", "/ext/share/**", "/ext/hls/**", "/ext/captions**"),
            SecurityRoutePolicy.EXT_ROLE_PATHS,
        )
        assertEquals(listOf("TEMP", "USER"), SecurityRoutePolicy.EXT_ROLE_NAMES)
    }
}
