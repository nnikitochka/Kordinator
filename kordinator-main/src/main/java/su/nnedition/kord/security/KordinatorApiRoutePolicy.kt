package su.nnedition.kord.security

/** Paths that remain available after retiring the legacy servlet API surface. */
object KordinatorApiRoutePolicy {
    @JvmStatic
    fun isSupported(path: String): Boolean {
        val applicationPath = path.removePrefix(INTERNAL_SERVLET_PREFIX)
        return applicationPath == API_PREFIX || applicationPath.startsWith("$API_PREFIX/")
    }

    private const val API_PREFIX = "/api/v1"
    private const val INTERNAL_SERVLET_PREFIX = "/internal/ktor"
}
