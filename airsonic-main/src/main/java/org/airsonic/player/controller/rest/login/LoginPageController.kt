package org.airsonic.player.controller.rest.login

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.util.StringUtil

/** Supplies the model and legacy redirect used by the login page rendered by the servlet adapter. */
class LoginPageController(
    private val securityService: SecurityService,
    private val settingsService: SettingsService
) {
    fun legacyRedirect(request: HttpServletRequest): String? {
        val username = request.getParameter("user") ?: return null
        val password = request.getParameter("password") ?: return null
        return "/login?$FORM_USERNAME_PARAMETER=" +
            StringUtil.urlEncode(username) + "&$FORM_PASSWORD_PARAMETER=" +
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
        private const val FORM_USERNAME_PARAMETER = "username"
        private const val FORM_PASSWORD_PARAMETER = "password"
        @JvmField val PATHS = arrayOf("/login", "/login.view")
    }
}
