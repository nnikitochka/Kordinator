package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.util.StringUtil
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.RedirectView

/**
 * Spring MVC Controller that serves the login page.
 */
@Controller
@RequestMapping("/login")
class LoginController(private val securityService: SecurityService, private val settingsService: SettingsService) {
    @GetMapping
    fun login(request: HttpServletRequest, response: HttpServletResponse): ModelAndView {
        // Auto-login if "user" and "password" parameters are given.
        var username = request.getParameter("user")
        var password = request.getParameter("password")
        if (username != null && password != null) {
            username = StringUtil.urlEncode(username)
            password = StringUtil.urlEncode(password)
            return ModelAndView(RedirectView(
                "/login?" +
                        UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY + "=" + username +
                        "&" + UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_PASSWORD_KEY + "=" + password
            ))
        }

        val map = buildMap {
            put("logout", request.getParameter("logout") != null)
            put("error", request.getParameter("error") != null)
            put("brand", settingsService.brand)
            put("loginMessage", settingsService.loginMessage)

            if (securityService.checkDefaultAdminCredsPresent()) {
                put("insecure", true)
            }
        }

        return ModelAndView("login", "model", map)
    }
}
