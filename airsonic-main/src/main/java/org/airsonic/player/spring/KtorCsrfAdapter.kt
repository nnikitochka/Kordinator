package org.airsonic.player.spring

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository
import org.springframework.security.web.util.matcher.RequestMatcher

/** Temporary servlet adapter for the shared CSRF policy and session token contract. */
internal class KtorCsrfAdapter(csrfRequestMatcher: RequestMatcher) {
    private val filter = CsrfFilter(HttpSessionCsrfTokenRepository()).apply {
        setRequireCsrfProtectionMatcher(csrfRequestMatcher)
    }

    fun service(request: HttpServletRequest, response: HttpServletResponse, terminal: FilterChain) {
        filter.doFilter(request, response, terminal)
    }
}
