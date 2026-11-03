package org.airsonic.player.controller.rest.hls

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.response.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Player
import org.airsonic.player.service.hls.HlsPlaylistService
import org.springframework.security.core.Authentication

class HlsRestController(private val playlists: HlsPlaylistService) {
    data class Request(val authentication: Authentication, val mediaFile: MediaFile, val player: Player,
                       val basePath: String, val prefix: String, val bitRates: Array<String>?, val size: String?)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
                head(path) { respond(call, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val body = playlists.generate(request.authentication, request.mediaFile, request.player,
            request.basePath, request.prefix, request.bitRates, request.size)
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respondText(body, ContentType.parse("application/x-mpegurl; charset=UTF-8"))
    }

    companion object {
        @JvmField val PATHS = arrayOf("/rest/hls", "/rest/hls.view")
    }
}
