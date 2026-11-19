package org.airsonic.player.security

/** Route groups and required roles shared by the current Spring adapter and the future Ktor security pipeline. */
object SecurityRoutePolicy {
    data class RoleRule(val paths: List<String>, val role: String)

    /** Matches the trailing wildcards used by these route policies without framework routing utilities. */
    @JvmStatic
    fun matchesPathPattern(pattern: String, path: String): Boolean {
        val wildcardIndex = pattern.indexOf('*')
        if (wildcardIndex < 0) return pattern == path

        val prefix = pattern.substring(0, wildcardIndex)
        val wildcard = pattern.substring(wildcardIndex)
        if (wildcard.startsWith("**")) return path == prefix.removeSuffix("/") || path.startsWith(prefix)
        return path.startsWith(prefix) && path.substring(prefix.length).none { it == '/' }
    }

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
