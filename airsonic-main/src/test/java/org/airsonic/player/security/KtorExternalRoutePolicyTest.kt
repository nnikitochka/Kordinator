package org.airsonic.player.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorExternalRoutePolicyTest {
    @Test
    fun matchesExternalRoutesRegisteredOnKtorServlet() {
        listOf(
            "/api/v1/auth/login",
            "/api/v1/me",
            "/api/v1/tracks/42/stream",
            "/ext/stream",
            "/ext/stream/song.mp3",
            "/ext/rest/share/PublicName",
            "/ext/hls/hls.m3u8",
            "/ext/hls/segment/segment.ts",
            "/ext/rest-hls/hls.m3u8",
            "/ext/captions/list",
            "/ext/coverArt.view",
        ).forEach { path -> assertTrue(path, KtorExternalRoutePolicy.matches(path)) }
    }

    @Test
    fun leavesUnmappedExternalPathsForCorsFallback() {
        listOf("/ext/hls/test", "/ext/captions/test", "/ext/unknown")
            .forEach { path -> assertFalse(path, KtorExternalRoutePolicy.matches(path)) }
    }
}
