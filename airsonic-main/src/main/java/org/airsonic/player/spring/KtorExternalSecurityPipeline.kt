package org.airsonic.player.spring

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.security.ExternalJwtPrincipal
import org.airsonic.player.security.JwtTokenVerifier
import org.airsonic.player.security.SecurityRoutePolicy
import org.airsonic.player.service.JWTSecurityService
import org.airsonic.player.service.SettingsService
import java.security.Principal

/** Temporary servlet boundary adapter for the stateless external player routes. */
internal class KtorExternalSecurityPipeline(
    private val settingsService: SettingsService,
) {
    private val corsAdapter = KtorCorsAdapter()
    private val csrfAdapter = KtorCsrfAdapter()
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
        val principal = ExternalJwtPrincipal(
            token.subject,
            token.getClaim(JWTSecurityService.CLAIM_PATH).asString(),
            token.expiresAt?.toInstant(),
            JWT_ROLES,
        )

        if (!isAuthorizedForPath(request, principal)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN)
            return
        }

        terminal.doFilter(AuthenticatedRequest(request, principal), response)
    }

    private fun isAuthorizedForPath(request: HttpServletRequest, principal: ExternalJwtPrincipal): Boolean {
        val path = request.servletPath + (request.pathInfo ?: "")
        val requiresRole = SecurityRoutePolicy.EXT_ROLE_PATHS.any { SecurityRoutePolicy.matchesPathPattern(it, path) }
        return !requiresRole || SecurityRoutePolicy.EXT_ROLE_NAMES.any(principal::hasRole)
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
        private val principal: ExternalJwtPrincipal,
    ) : HttpServletRequestWrapper(request) {
        override fun getUserPrincipal(): Principal = principal

        override fun getRemoteUser(): String = principal.name

        override fun isUserInRole(role: String): Boolean = principal.hasRole(role)
    }

    private companion object {
        const val FAILURE_URL = "/login?error"
        val JWT_ROLES = setOf("ROLE_TEMP")
    }
}
