package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.service.SecurityService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.RedirectView

/**
 * Controller for the main settings page.
 */
@Controller
@RequestMapping("/settings")
class SettingsController(private val securityService: SecurityService) {
    @GetMapping
    protected fun handleRequestInternal(request: HttpServletRequest?): ModelAndView {
        val user = securityService.getCurrentUser(request)

        // Redirect to music folder settings if admin.
        val view = if (user.isAdminRole) "musicFolderSettings"
        else "personalSettings"

        return ModelAndView(RedirectView(view))
    }
}
