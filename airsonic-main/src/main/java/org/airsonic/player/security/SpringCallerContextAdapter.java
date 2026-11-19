package org.airsonic.player.security;

import jakarta.servlet.http.HttpServletRequest;
import org.airsonic.player.service.SecurityService;
import org.springframework.security.core.Authentication;

/**
 * Transitional servlet adapter. This is the only security migration boundary
 * that reads Spring's thread-local context; application services receive the
 * resulting immutable {@link CallerContext} instead.
 */
public final class SpringCallerContextAdapter {
    private final SecurityService securityService;

    public SpringCallerContextAdapter(SecurityService securityService) {
        this.securityService = securityService;
    }

    public CallerContext fromRequest(HttpServletRequest request) {
        Authentication authentication = request.getUserPrincipal() instanceof Authentication
                ? (Authentication) request.getUserPrincipal()
                : null;
        if (authentication == null || !authentication.isAuthenticated()) {
            return CallerContext.anonymous();
        }
        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            return CallerContext.anonymous();
        }
        return securityService.createCallerContext(username, true);
    }
}
