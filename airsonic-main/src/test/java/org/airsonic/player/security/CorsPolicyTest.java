package org.airsonic.player.security;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CorsPolicyTest {
    private final CorsPolicy policy = CorsPolicy.defaults();

    @Test
    public void exposesWildcardOriginsAndMethods() {
        assertEquals(java.util.List.of("*"), policy.allowedOrigins());
        assertEquals(java.util.List.of("*"), policy.allowedMethods());
    }

    @Test
    public void matchesExistingFamiliesAndTheirBoundaries() {
        for (String path : java.util.List.of("/rest", "/rest/ping", "/stream", "/stream/song",
                "/hls", "/hls2/segment", "/captionsx", "/ext/stream", "/ext/stream/song",
                "/ext/hls2/segment", "/ext/captionsx")) {
            assertTrue(path, policy.appliesTo(path));
        }
        for (String path : java.util.List.of("/restful", "/streaming", "/ext/streaming",
                "/api/rest/ping", "/", "/extension/hls")) {
            assertFalse(path, policy.appliesTo(path));
        }
    }

    @Test
    public void stripsContextPathWithExistingStartsWithSemantics() {
        assertEquals("/rest/ping", policy.applicationPath("/airsonic/rest/ping", "/airsonic"));
        assertEquals("/other", policy.applicationPath("/other", "/airsonic"));
        assertEquals("sonic/rest", policy.applicationPath("/airsonic/rest", "/air"));
    }
}
