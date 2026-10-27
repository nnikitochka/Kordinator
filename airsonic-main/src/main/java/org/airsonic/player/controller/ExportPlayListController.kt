package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.util.StringUtil
import org.springframework.stereotype.Controller
import org.springframework.web.bind.ServletRequestUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

/**
 * Spring MVC Controller that serves the login page.
 */
@Controller
@RequestMapping("/exportPlaylist")
class ExportPlayListController(
    private val playlistService: PlaylistService,
    private val securityService: SecurityService
) {
    @GetMapping
    @Throws(Exception::class)
    fun exportPlaylist(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
        val id = ServletRequestUtils.getRequiredIntParameter(request, "id")
        val playlist = playlistService.getPlaylist(id)
        if (!playlistService.isReadAllowed(playlist, securityService.getCurrentUsername(request))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN)
            return null
        }
        response.contentType = "application/x-download"
        response.setHeader(
            "Content-Disposition",
            "attachment; filename=\"" + StringUtil.fileSystemSafe(playlist.name) + ".m3u8\""
        )

        playlistService.exportPlaylist(id, response.outputStream)
        return null
    }
}
