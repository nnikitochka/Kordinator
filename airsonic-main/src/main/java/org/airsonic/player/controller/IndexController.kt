package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("/index")
class IndexController(private val securityService: SecurityService, private val settingsService: SettingsService) {
    @GetMapping
    fun index(request: HttpServletRequest?): ModelAndView {
        val userSettings = settingsService.getUserSettings(securityService.getCurrentUsername(request))

        val map = mapOf(
            "showRight" to userSettings.showNowPlayingEnabled,
            "autoHidePlayQueue" to userSettings.autoHidePlayQueue,
            "keyboardShortcutsEnabled" to userSettings.keyboardShortcutsEnabled,
            "showSideBar" to userSettings.showSideBar,
            "brand" to settingsService.brand
        )
        return ModelAndView("index", "model", map)
    }
}
