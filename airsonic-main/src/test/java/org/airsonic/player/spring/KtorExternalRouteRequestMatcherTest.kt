package org.airsonic.player.spring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.mock.web.MockHttpServletRequest

class KtorExternalRouteRequestMatcherTest {
    private val matcher = KtorExternalRouteRequestMatcher()

    @Test
    fun matchesExternalRoutesRegisteredOnKtorServlet() {
        listOf(
            "/ext/stream",
            "/ext/stream/song.mp3",
            "/ext/share/PublicName",
            "/ext/rest/share/PublicName",
            "/ext/hls/hls.m3u8",
            "/ext/hls/segment/segment.ts",
            "/ext/rest-hls/hls.m3u8",
            "/ext/captions/list",
            "/ext/coverArt.view",
        ).forEach { path -> assertTrue(path, matcher.matches(request(path))) }
    }

    @Test
    fun leavesUnmappedExternalPathsForCorsFallback() {
        listOf("/ext/hls/test", "/ext/captions/test", "/ext/unknown").forEach { path ->
            assertFalse(path, matcher.matches(request(path)))
        }
    }

    private fun request(path: String): MockHttpServletRequest = MockHttpServletRequest().apply {
        requestURI = path
        servletPath = path
    }
}
