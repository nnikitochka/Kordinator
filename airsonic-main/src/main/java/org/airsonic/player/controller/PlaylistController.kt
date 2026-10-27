package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.ServletRequestUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.RedirectView

/**
 * Controller for the playlist page.
 */
@Controller
@RequestMapping("/playlist")
class PlaylistController(
    private val securityService: SecurityService,
    private val playlistService: PlaylistService,
    private val settingsService: SettingsService,
    private val playerService: PlayerService
) {
    @GetMapping
    @Throws(Exception::class)
    protected fun handleRequestInternal(request: HttpServletRequest, response: HttpServletResponse?): ModelAndView {
        val id = ServletRequestUtils.getRequiredIntParameter(request, "id")
        val user = securityService.getCurrentUser(request)
        val username = user.username
        val userSettings = settingsService.getUserSettings(username)
        val player = playerService.getPlayer(request, response)
        val playlist = playlistService.getPlaylist(id)
            ?: return ModelAndView(RedirectView("notFound"))

        val map = mapOf(
            "playlist" to playlist,
            "user" to user,
            "player" to player,
            "visibility" to userSettings.playlistVisibility,
            "editAllowed" to (username == playlist.username || securityService.isAdmin(username)),
            "partyMode" to userSettings.partyModeEnabled,
            "initialPaginationSize" to userSettings.paginationSizePlaylist,
        )

        return ModelAndView("playlist", "model", map)
    }
}
