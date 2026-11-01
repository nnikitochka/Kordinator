package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.UPnPService
import org.apache.commons.lang.StringUtils
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.ServletRequestUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * Controller for the page used to administrate the UPnP/DLNA server settings.
 * 
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/dlnaSettings")
class DLNASettingsController(private var upnpService: UPnPService, private var settingsService: SettingsService) {
    @GetMapping
    fun handleGet(model: Model): String {
        val map = mapOf(
            "dlnaEnabled" to settingsService.isDlnaEnabled,
            "dlnaServerName" to settingsService.dlnaServerName,
            "dlnaBaseLANURL" to settingsService.dlnaBaseLANURL,
        )

        model.addAttribute("model", map)
        return "dlnaSettings"
    }

    @PostMapping
    fun handlePost(request: HttpServletRequest, redirectAttributes: RedirectAttributes): String {
        handleParameters(request)
        redirectAttributes.addFlashAttribute("settings_toast", true)
        return "redirect:dlnaSettings.view"
    }

    private fun handleParameters(request: HttpServletRequest) {
        val dlnaEnabled = ServletRequestUtils.getBooleanParameter(request, "dlnaEnabled", false)
        var dlnaServerName = StringUtils.trimToNull(request.getParameter("dlnaServerName"))
        val dlnaBaseLANURL = StringUtils.trimToNull(request.getParameter("dlnaBaseLANURL"))
        if (dlnaServerName == null) {
            dlnaServerName = "Airsonic"
        }

        upnpService.setMediaServerEnabled(false)
        settingsService.isDlnaEnabled = dlnaEnabled
        settingsService.dlnaServerName = dlnaServerName
        settingsService.dlnaBaseLANURL = dlnaBaseLANURL
        settingsService.save()
        upnpService.setMediaServerEnabled(dlnaEnabled)
    }

    fun setSettingsService(settingsService: SettingsService) {
        this.settingsService = settingsService
    }

    fun setUpnpService(upnpService: UPnPService) {
        this.upnpService = upnpService
    }
}
