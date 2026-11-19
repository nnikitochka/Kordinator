package org.airsonic.player.security

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.service.SecurityService

/** Converts the servlet principal into the application's immutable caller context. */
class ServletCallerContextAdapter(private val securityService: SecurityService) {
    fun fromRequest(request: HttpServletRequest): CallerContext {
        val username = request.userPrincipal?.name?.takeIf { it.isNotBlank() }
            ?: return CallerContext.anonymous()
        return securityService.createCallerContext(username, true)
    }
}
