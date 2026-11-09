package org.airsonic.player.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.web.util.matcher.RegexRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.stereotype.Component
import java.util.*

/**
 * See
 * http://blogs.sourceallies.com/2014/04/customizing-csrf-protection-in-spring-security/
 * https://docs.spring.io/spring-security/site/docs/current/reference/html/appendix-namespace.html#nsa-csrf
 * 
 */
@Component
class CsrfSecurityRequestMatcher : RequestMatcher {
    private val whiteListedMatchers = listOf(
        RegexRequestMatcher("/rest/.*\\.view(\\?.*)?", "POST"),
        RegexRequestMatcher("/search(?:\\.view)?", "POST")
    )

    override fun matches(request: HttpServletRequest): Boolean {
        val skipCSRF = allowedMethods.contains(request.method) ||
                whiteListedMatchers.stream().anyMatch { matcher -> matcher.matches(request) }
        return !skipCSRF
    }

    companion object {
        private val allowedMethods = listOf("GET", "HEAD", "TRACE", "OPTIONS")
    }
}
