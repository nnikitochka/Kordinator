package org.airsonic.player.controller.rest.nowplaying

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.StatusService

/** Ktor handler for the website's "currently playing" navigation. */
class NowPlayingPageController(
    private val playerService: PlayerService,
    private val statusService: StatusService,
    private val mediaFileService: MediaFileService,
) {
    data class Request(val destination: String)

    fun request(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): Request {
        val player = playerService.getPlayer(servletRequest, servletResponse)
        val status = statusService.getStreamStatusesForPlayer(player).firstOrNull()
            ?: statusService.getInactiveStreamStatusForPlayer(player)
        val destination = status?.let {
            val mediaFile = it.mediaFile ?: mediaFileService.getMediaFile(it.externalFile)
            mediaFileService.getParentOf(mediaFile)
                .takeUnless(mediaFileService::isRoot)
                ?.let { parent -> "main.view?id=${parent.id}" }
        } ?: "home.view"
        return Request(destination)
    }

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { call.respondRedirect(request(call).destination) }
                head(path) { call.respondRedirect(request(call).destination) }
            }
        }
    }

    companion object {
        const val REQUEST_ATTRIBUTE = "org.airsonic.player.nowPlayingPageRequest"
        @JvmField val PATHS = arrayOf("/nowPlaying", "/nowPlaying.view")
    }
}
