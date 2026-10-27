package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.service.SettingsService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.RedirectView

@Controller
@RequestMapping("/gettingStarted")
class GettingStartedController(private val settingsService: SettingsService) {
    @GetMapping
    fun gettingStarted(request: HttpServletRequest): ModelAndView {
        if (request.getParameter("hide") != null) {
            settingsService.isGettingStartedEnabled = false
            settingsService.save()
            return ModelAndView(RedirectView("home.view"))
        }

        val map = mapOf("runningAsRoot" to ("root" == System.getProperty("user.name")))
        return ModelAndView("gettingStarted", "model", map)
    }
}
