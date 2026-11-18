package org.airsonic.player.spring

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.security.JWTAuthenticationToken
import org.airsonic.player.security.JwtTokenVerifier
import org.airsonic.player.security.SecurityRoutePolicy
import org.airsonic.player.service.SettingsService
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.util.AntPathMatcher
import java.security.Principal

/** Temporary servlet boundary adapter for the stateless external player routes. */
internal class KtorExternalSecurityPipeline(
    private val settingsService: SettingsService,
    csrfRequestMatcher: org.springframework.security.web.util.matcher.RequestMatcher,
) {
    private val corsAdapter = KtorCorsAdapter()
    private val csrfAdapter = KtorCsrfAdapter(csrfRequestMatcher)
    private val antPathMatcher = AntPathMatcher()
    private val jwtTokenVerifier = JwtTokenVerifier()

    fun service(request: HttpServletRequest, response: HttpServletResponse, terminal: FilterChain) {
        if (!isExternalPath(request.servletPath)) {
            terminal.doFilter(request, response)
            return
        }

        writeSecurityHeaders(request, response)
        corsAdapter.service(request, response) { corsRequest, corsResponse ->
            csrfAdapter.service(
                corsRequest as HttpServletRequest,
                corsResponse as HttpServletResponse
            ) { csrfRequest, csrfResponse ->
                authenticate(
                    csrfRequest as HttpServletRequest,
                    csrfResponse as HttpServletResponse,
                    terminal,
                )
            }
        }
    }

    private fun authenticate(request: HttpServletRequest, response: HttpServletResponse, terminal: FilterChain) {
        val rawToken = request.getParameter("jwt")
        if (rawToken.isNullOrEmpty()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN)
            return
        }

        val requestedPath = request.requestURI.substring(request.contextPath.length + 1) + "?" + request.queryString
        val verification = jwtTokenVerifier.verifyRequest(settingsService.jwtKey, rawToken, requestedPath)
        if (!verification.isVerified) {
            response.sendRedirect(request.contextPath + FAILURE_URL)
            return
        }
        val token = verification.token
        val authentication = JWTAuthenticationToken(
            token.subject,
            rawToken,
            requestedPath,
            JWT_AUTHORITIES,
            token,
        )

        if (!authentication.isAuthenticated || !isAuthorizedForPath(request, authentication)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN)
            return
        }

        terminal.doFilter(AuthenticatedRequest(request, authentication), response)
    }

    private fun isAuthorizedForPath(request: HttpServletRequest, authentication: Authentication): Boolean {
        val path = request.servletPath + (request.pathInfo ?: "")
        val requiresRole = SecurityRoutePolicy.EXT_ROLE_PATHS.any { antPathMatcher.match(it, path) }
        return !requiresRole || SecurityRoutePolicy.EXT_ROLE_NAMES.any { role ->
            authentication.authorities.any { it.authority == "ROLE_$role" }
        }
    }

    private fun isExternalPath(servletPath: String): Boolean =
        servletPath == "/ext" || servletPath.startsWith("/ext/")

    private fun writeSecurityHeaders(request: HttpServletRequest, response: HttpServletResponse) {
        response.setHeader("X-Frame-Options", "SAMEORIGIN")
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
        response.setHeader("Pragma", "no-cache")
        response.setHeader("Expires", "0")
        if (request.isSecure) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains")
        }
    }

    private class AuthenticatedRequest(
        request: HttpServletRequest,
        private val authentication: Authentication,
    ) : HttpServletRequestWrapper(request) {
        override fun getUserPrincipal(): Principal = authentication

        override fun getRemoteUser(): String = authentication.name

        override fun isUserInRole(role: String): Boolean = authentication.authorities.any {
            it.authority == "ROLE_$role"
        }
    }

    private companion object {
        const val FAILURE_URL = "/login?error"
        val JWT_AUTHORITIES = mutableListOf(
            SimpleGrantedAuthority("IS_AUTHENTICATED_FULLY"),
            SimpleGrantedAuthority("ROLE_TEMP"),
        )
    }
}
