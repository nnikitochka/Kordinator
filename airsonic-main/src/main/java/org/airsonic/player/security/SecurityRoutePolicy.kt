package org.airsonic.player.security

/** Route groups and required roles shared by the current Spring adapter and the future Ktor security pipeline. */
object SecurityRoutePolicy {
    data class RoleRule(val paths: List<String>, val role: String)

    @JvmField
    val WEB_PUBLIC_PATHS = listOf(
        "/rest/recover", "/recover*", "/accessDenied*", "/style/**", "/icons/**", "/flash/**", "/script/**",
        "/login", "/error",
    )

    @JvmField
    val WEB_ROLE_RULES = listOf(
        RoleRule(listOf("/personalSettings*", "/playerSettings*", "/shareSettings*", "/credentialsSettings*"), "SETTINGS"),
        RoleRule(
            listOf(
                "/generalSettings*", "/advancedSettings*", "/userSettings*", "/musicFolderSettings*",
                "/databaseSettings*", "/transcodeSettings*", "/rest/startScan*",
            ),
            "ADMIN",
        ),
        RoleRule(listOf("/deletePlaylist*", "/savePlaylist*"), "PLAYLIST"),
        RoleRule(listOf("/upload*"), "UPLOAD"),
        RoleRule(listOf("/createShare*"), "SHARE"),
        RoleRule(listOf("/changeCoverArt*", "/editTags*"), "COVERART"),
    )

    @JvmField
    val EXT_ROLE_PATHS = listOf(
        "/ext/stream/**", "/ext/coverArt*", "/ext/share/**", "/ext/hls/**", "/ext/captions**",
    )

    @JvmField
    val EXT_ROLE_NAMES = listOf("TEMP", "USER")
}
