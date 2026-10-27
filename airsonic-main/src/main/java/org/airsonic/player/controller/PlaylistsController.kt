package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

/**
 * Controller for the playlists page.
 */
@Controller
@RequestMapping("/playlists")
class PlaylistsController(private val securityService: SecurityService, private val settingsService: SettingsService) {
    @GetMapping
    fun doGet(request: HttpServletRequest?, model: Model): String {
        val user = securityService.getCurrentUser(request)
        val userSettings = settingsService.getUserSettings(user.username)

        val map = mapOf(
            "username" to user.username,
            "viewAsList" to userSettings.viewAsList,
            "initialPaginationSize" to userSettings.paginationSizePlaylist
        )
        model.addAttribute("model", map)
        return "playlists"
    }
}
