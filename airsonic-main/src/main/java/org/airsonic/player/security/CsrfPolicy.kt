package org.airsonic.player.security

/** Framework-neutral decision for whether a request needs CSRF protection. */
class CsrfPolicy {
    fun requiresProtection(method: String, servletPath: String, pathInfo: String?, queryString: String?): Boolean {
        if (method in SAFE_METHODS) return false

        val requestPath = buildString {
            append(servletPath)
            pathInfo?.let(::append)
            queryString?.let {
                append('?')
                append(it)
            }
        }

        if (method == "POST" && (REST_VIEW.matches(requestPath) || SEARCH.matches(requestPath))) return false
        return true
    }

    private companion object {
        val SAFE_METHODS = setOf("GET", "HEAD", "TRACE", "OPTIONS")
        val REST_VIEW = Regex("/rest/.*\\.view(\\?.*)?")
        val SEARCH = Regex("/search(?:\\.view)?")
    }
}
