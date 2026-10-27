package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.VersionService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

/**
 * Controller for the right frame.
 */
@Controller
@RequestMapping("/right")
class RightController(
    private val settingsService: SettingsService,
    private val securityService: SecurityService,
    private val versionService: VersionService
) {
    @GetMapping
    protected fun handleRequestInternal(request: HttpServletRequest?): ModelAndView {
        val map: MutableMap<String, Any> = HashMap()
        val result = ModelAndView("right")

        val userSettings = settingsService.getUserSettings(securityService.getCurrentUsername(request))
        if (userSettings.finalVersionNotificationEnabled && versionService.isNewFinalVersionAvailable()) {
            map["newVersionAvailable"] = true
            map["latestVersion"] = versionService.getLatestFinalVersion()
        } else if (userSettings.betaVersionNotificationEnabled && versionService.isNewBetaVersionAvailable()) {
            map["newVersionAvailable"] = true
            map["latestVersion"] = versionService.getLatestBetaVersion()
        }

        map["brand"] = settingsService.brand
        map["showNowPlaying"] = userSettings.showNowPlayingEnabled
        map["user"] = securityService.getCurrentUser(request)

        result.addObject("model", map)
        return result
    }
}
