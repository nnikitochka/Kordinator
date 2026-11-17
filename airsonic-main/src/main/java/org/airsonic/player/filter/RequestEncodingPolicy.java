package org.airsonic.player.filter;

import java.nio.charset.StandardCharsets;

/** Shared default charset for servlet requests and the Ktor-to-JSP bridge. */
public final class RequestEncodingPolicy {
    public static final String DEFAULT_ENCODING = StandardCharsets.UTF_8.name();

    private RequestEncodingPolicy() {
    }
}
