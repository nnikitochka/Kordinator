package org.airsonic.player.security;

import java.util.List;

/** Framework-neutral description of the application's CORS policy. */
public final class CorsPolicy {
    private static final CorsPolicy DEFAULT = new CorsPolicy(
            List.of("*"), List.of("*"));

    private final List<String> allowedOrigins;
    private final List<String> allowedMethods;

    private CorsPolicy(List<String> allowedOrigins, List<String> allowedMethods) {
        this.allowedOrigins = List.copyOf(allowedOrigins);
        this.allowedMethods = List.copyOf(allowedMethods);
    }

    public static CorsPolicy defaults() {
        return DEFAULT;
    }

    public List<String> allowedOrigins() {
        return allowedOrigins;
    }

    public List<String> allowedMethods() {
        return allowedMethods;
    }

    /** Matches the former servlet adapter's context-path removal, including its startsWith semantics. */
    public String applicationPath(String requestUri, String contextPath) {
        return requestUri.startsWith(contextPath) ? requestUri.substring(contextPath.length()) : requestUri;
    }

    public boolean appliesTo(String applicationPath) {
        return applicationPath.equals("/rest") || applicationPath.startsWith("/rest/")
                || applicationPath.equals("/stream") || applicationPath.startsWith("/stream/")
                || applicationPath.startsWith("/hls")
                || applicationPath.startsWith("/captions")
                || applicationPath.equals("/ext/stream") || applicationPath.startsWith("/ext/stream/")
                || applicationPath.startsWith("/ext/hls")
                || applicationPath.startsWith("/ext/captions");
    }
}
