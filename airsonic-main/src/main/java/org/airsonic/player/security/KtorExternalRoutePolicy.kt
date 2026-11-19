package org.airsonic.player.security

import org.airsonic.player.controller.captions.CaptionsRestController
import org.airsonic.player.controller.rest.cover.CoverArtRestController
import org.airsonic.player.controller.rest.hls.HlsRestController
import org.airsonic.player.controller.rest.hls.HlsSegmentRestController
import org.airsonic.player.controller.rest.stream.StreamRestController

/** Route classification shared by the servlet security adapter and the Ktor route set. */
object KtorExternalRoutePolicy {
    private val exactPaths = buildSet {
        addAll(CaptionsRestController.PATHS.filter { it.startsWith("/ext/") })
        addAll(CoverArtRestController.EXTERNAL_PATHS)
        addAll(HlsRestController.PATHS.filter { it.startsWith("/ext/") })
        addAll(HlsRestController.EXTERNAL_PATHS)
        addAll(HlsSegmentRestController.PATHS.filter { it.startsWith("/ext/") })
        addAll(StreamRestController.EXTERNAL_PATHS)
    }

    private val wildcardPaths = listOf(
        "/api/v1/**",
        "/ext/rest/share/**",
        "/ext/stream/**",
        "/ext/hls/segment/**",
    )

    @JvmStatic
    fun matches(path: String): Boolean = path in exactPaths || wildcardPaths.any { SecurityRoutePolicy.matchesPathPattern(it, path) }
}
