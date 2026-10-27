package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.domain.AvatarScheme
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

/**
 * Controller for the top frame.
 */
@Controller
@RequestMapping("/top")
class TopController(private val settingsService: SettingsService, private val securityService: SecurityService) {
    @GetMapping
    protected fun handleRequestInternal(request: HttpServletRequest?): ModelAndView {
        val user = securityService.getCurrentUser(request)
        val userSettings = settingsService.getUserSettings(user.username)

        val map = mapOf(
            "user" to user,
            "showSideBar" to userSettings.showSideBar,
            "showAvatar" to (userSettings.avatarScheme != AvatarScheme.NONE)
        )

        return ModelAndView("top", "model", map)
    }
}
