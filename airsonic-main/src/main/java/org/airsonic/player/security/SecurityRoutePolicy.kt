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
        "/rest/recover", "/error",
    )

    @JvmField
    val WEB_ROLE_RULES = listOf(
        RoleRule(listOf("/rest/startScan*"), "ADMIN"),
    )

    @JvmField
    val EXT_ROLE_PATHS = listOf(
        "/ext/stream/**", "/ext/coverArt*", "/ext/hls/**", "/ext/captions**",
    )

    @JvmField
    val EXT_ROLE_NAMES = listOf("TEMP", "USER")
}
