package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView

/**
 * Controller for the playlist frame.
 */
@Controller
@RequestMapping("/playQueue")
class PlayQueueController(
    private val playerService: PlayerService,
    private val securityService: SecurityService,
    private val settingsService: SettingsService
) {
    @GetMapping
    @Throws(Exception::class)
    protected fun handleRequestInternal(request: HttpServletRequest, response: HttpServletResponse?): ModelAndView {
        val user = securityService.getCurrentUser(request)
        val userSettings = settingsService.getUserSettings(user.username)

        val map = buildMap {
            put("user", user)
            put("players", playerService.getPlayersForUserAndClientId(user.username, null))
            put("visibility", userSettings.playqueueVisibility)
            put("partyMode", userSettings.partyModeEnabled)
            put("notify", userSettings.songNotificationEnabled)
            put("autoHide", userSettings.autoHidePlayQueue)
            put("initialPaginationSize", userSettings.paginationSizePlayqueue)
            put("autoBookmark", userSettings.autoBookmark)
            put("audioBookmarkFrequency", userSettings.audioBookmarkFrequency)
        }
        return ModelAndView("playQueue", "model", map)
    }

    @GetMapping("/player")
    @ResponseBody
    @Throws(Exception::class)
    fun getPlayer(request: HttpServletRequest, response: HttpServletResponse?): Map<String, Any> {
        val player = playerService.getPlayer(request, response)
        return mapOf(
            "id" to player.id,
            "description" to player.getShortDescription(),
            "tech" to player.technology,
        )
    }
}
