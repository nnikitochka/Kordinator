package org.airsonic.player.filter;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class StaticResponseHeadersTest {
    @Test
    public void appliesConfiguredHeaderNamesAndValuesInOrder() {
        Map<String, String> configured = new LinkedHashMap<>();
        configured.put("Cache-Control", "no-cache, post-check=0, pre-check=0");
        configured.put("Pragma", "no-cache");
        configured.put("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        StaticResponseHeaders headers = new StaticResponseHeaders(configured);
        Map<String, String> applied = new LinkedHashMap<>();

        headers.apply(applied::put);

        assertEquals(configured, applied);
        assertEquals(configured, headers.values());
    }

    @Test
    public void copiesInputAndDoesNotExposeMutableHeaderMap() {
        Map<String, String> configured = new LinkedHashMap<>();
        configured.put("Cache-Control", "max-age=36000");
        StaticResponseHeaders headers = new StaticResponseHeaders(configured);
        configured.put("Cache-Control", "no-store");

        assertEquals("max-age=36000", headers.values().get("Cache-Control"));
        assertThrows(UnsupportedOperationException.class, () -> headers.values().put("Pragma", "no-cache"));
    }

    @Test
    public void writesEachConfiguredHeaderOnce() {
        StaticResponseHeaders headers = new StaticResponseHeaders(Map.of("Cache-Control", "max-age=36000"));
        AtomicInteger writes = new AtomicInteger();

        headers.apply((name, value) -> writes.incrementAndGet());

        assertEquals(1, writes.get());
    }
}
