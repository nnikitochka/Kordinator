package org.airsonic.player.security;

import java.util.Set;
import java.util.regex.Pattern;

/** Framework-neutral decision for whether a request needs CSRF protection. */
public final class CsrfPolicy {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "TRACE", "OPTIONS");
    private static final Pattern REST_VIEW = Pattern.compile("/rest/.*\\.view(\\?.*)?");
    private static final Pattern SEARCH = Pattern.compile("/search(?:\\.view)?");

    public boolean requiresProtection(String method, String servletPath, String pathInfo, String queryString) {
        if (SAFE_METHODS.contains(method)) {
            return false;
        }

        String requestPath = servletPath;
        if (pathInfo != null) {
            requestPath += pathInfo;
        }
        if (queryString != null) {
            requestPath += "?" + queryString;
        }

        if ("POST".equals(method) && (REST_VIEW.matcher(requestPath).matches() || SEARCH.matcher(requestPath).matches())) {
            return false;
        }
        return true;
    }
}
