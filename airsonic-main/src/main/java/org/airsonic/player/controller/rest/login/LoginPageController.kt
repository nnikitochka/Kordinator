package org.airsonic.player.controller.rest.login

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.util.StringUtil
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/** Supplies the model and legacy redirect used by the login page rendered by the servlet adapter. */
class LoginPageController(
    private val securityService: SecurityService,
    private val settingsService: SettingsService
) {
    fun legacyRedirect(request: HttpServletRequest): String? {
        val username = request.getParameter("user") ?: return null
        val password = request.getParameter("password") ?: return null
        return "/login?${UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY}=" +
            StringUtil.urlEncode(username) + "&${UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_PASSWORD_KEY}=" +
            StringUtil.urlEncode(password)
    }

    fun jspModel(request: HttpServletRequest): Map<String, Any?> = buildMap {
        put("logout", request.getParameter("logout") != null)
        put("error", request.getParameter("error") != null)
        put("brand", settingsService.brand)
        put("loginMessage", settingsService.loginMessage)
        if (securityService.checkDefaultAdminCredsPresent()) {
            put("insecure", true)
        }
    }

    companion object {
        @JvmField val PATHS = arrayOf("/login", "/login.view")
    }
}
