package org.airsonic.player.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * See
 * http://blogs.sourceallies.com/2014/04/customizing-csrf-protection-in-spring-security/
 * https://docs.spring.io/spring-security/site/docs/current/reference/html/appendix-namespace.html#nsa-csrf
 * 
 */
class CsrfSecurityRequestMatcher : RequestMatcher {
    private val policy = CsrfPolicy()

    override fun matches(request: HttpServletRequest): Boolean {
        return policy.requiresProtection(request.method, request.servletPath, request.pathInfo, request.queryString)
    }
}
