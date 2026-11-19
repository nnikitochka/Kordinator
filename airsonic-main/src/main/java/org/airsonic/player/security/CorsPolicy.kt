package org.airsonic.player.security

/** Framework-neutral description of the application's CORS policy. */
class CorsPolicy private constructor(
    private val origins: List<String>,
    private val methods: List<String>,
) {
    fun allowedOrigins(): List<String> = origins

    fun allowedMethods(): List<String> = methods

    /** Matches the former servlet adapter's context-path removal, including its startsWith semantics. */
    fun applicationPath(requestUri: String, contextPath: String): String =
        if (requestUri.startsWith(contextPath)) requestUri.substring(contextPath.length) else requestUri

    fun appliesTo(applicationPath: String): Boolean =
        applicationPath == "/rest" || applicationPath.startsWith("/rest/") ||
            applicationPath.startsWith("/captions") ||
            applicationPath == "/ext/stream" || applicationPath.startsWith("/ext/stream/") ||
            applicationPath.startsWith("/ext/hls") ||
            applicationPath.startsWith("/ext/captions")

    companion object {
        private val DEFAULT = CorsPolicy(listOf("*"), listOf("*"))

        @JvmStatic
        fun defaults(): CorsPolicy = DEFAULT
    }
}
