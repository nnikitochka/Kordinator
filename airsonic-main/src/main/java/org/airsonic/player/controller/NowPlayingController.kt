package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.StatusService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.RedirectView

/**
 * Controller for showing what's currently playing.
 */
@Controller
@RequestMapping("/nowPlaying")
class NowPlayingController(
    private val playerService: PlayerService,
    private val statusService: StatusService,
    private val mediaFileService: MediaFileService
) {

    @GetMapping
    protected fun handleRequestInternal(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ModelAndView {
        val player = playerService.getPlayer(request, response)

        val status = statusService.getStreamStatusesForPlayer(player).firstOrNull()
            ?: statusService.getInactiveStreamStatusForPlayer(player)

        val url = status?.let { status ->
            val mediaFile = status.mediaFile
                ?: mediaFileService.getMediaFile(status.externalFile)

            mediaFileService.getParentOf(mediaFile)
                .takeUnless(mediaFileService::isRoot)
                ?.let { "main.view?id=${it.id}" }
        } ?: "home.view"

        return ModelAndView(RedirectView(url))
    }
}