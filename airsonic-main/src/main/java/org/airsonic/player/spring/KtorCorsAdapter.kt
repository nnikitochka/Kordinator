package org.airsonic.player.spring

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.security.CorsPolicy

/** Applies the framework-neutral CORS policy at the Ktor servlet boundary. */
internal class KtorCorsAdapter(private val policy: CorsPolicy = CorsPolicy.defaults()) {
    fun service(request: HttpServletRequest, response: HttpServletResponse, terminal: FilterChain) {
        val origin = request.getHeader("Origin")
        val applicationPath = policy.applicationPath(request.requestURI, request.contextPath)
        if (origin == null || !policy.appliesTo(applicationPath)) {
            terminal.doFilter(request, response)
            return
        }

        response.addHeader("Vary", "Origin")
        response.setHeader("Access-Control-Allow-Origin", "*")
        if (request.method == "OPTIONS" && request.getHeader("Access-Control-Request-Method") != null) {
            response.addHeader("Vary", "Access-Control-Request-Method")
            response.addHeader("Vary", "Access-Control-Request-Headers")
            val requestedMethod = request.getHeader("Access-Control-Request-Method")
            val allowedMethods = policy.allowedMethods()
            if ("*" in allowedMethods || requestedMethod in allowedMethods) {
                response.setHeader(
                    "Access-Control-Allow-Methods",
                    if ("*" in allowedMethods) requestedMethod else allowedMethods.joinToString(", "),
                )
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            request.getHeader("Access-Control-Request-Headers")?.let {
                response.setHeader("Access-Control-Allow-Headers", it)
            }
            response.status = HttpServletResponse.SC_OK
            return
        }

        terminal.doFilter(request, response)
    }
}
