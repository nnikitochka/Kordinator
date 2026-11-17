package org.airsonic.player.spring

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.controller.captions.CaptionsRestController
import org.airsonic.player.controller.rest.cover.CoverArtRestController
import org.airsonic.player.controller.rest.hls.HlsRestController
import org.airsonic.player.controller.rest.hls.HlsSegmentRestController
import org.airsonic.player.controller.rest.stream.StreamRestController
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.util.AntPathMatcher

/** Identifies external routes explicitly registered on the Ktor servlet. */
class KtorExternalRouteRequestMatcher : RequestMatcher {
    private val pathMatcher = AntPathMatcher()

    override fun matches(request: HttpServletRequest): Boolean {
        val path = request.servletPath + (request.pathInfo ?: "")
        return path in exactPaths || wildcardPaths.any { pathMatcher.match(it, path) }
    }

    private companion object {
        val exactPaths = buildSet {
            addAll(CaptionsRestController.PATHS.filter { it.startsWith("/ext/") })
            addAll(CoverArtRestController.LEGACY_PATHS.filter { it.startsWith("/ext/") })
            addAll(HlsRestController.PATHS.filter { it.startsWith("/ext/") })
            addAll(HlsRestController.WEBSITE_PATHS.filter { it.startsWith("/ext/") })
            addAll(HlsSegmentRestController.PATHS.filter { it.startsWith("/ext/") })
            addAll(StreamRestController.WEBSITE_PATHS.filter { it.startsWith("/ext/") })
        }

        val wildcardPaths = listOf(
            "/ext/share/**",
            "/ext/rest/share/**",
            "/ext/stream/**",
            "/ext/hls/segment/**",
        )
    }
}
