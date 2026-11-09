package org.airsonic.player.controller.rest.video

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.User
import org.airsonic.player.service.VideoPlaybackService

class VideoPlayerRestController(private val playback: VideoPlaybackService, private val json: ObjectMapper) {
    data class Request(val file: MediaFile, val user: User, val playerId: Int, val baseUrl: String, val position: Long?,
                       val csrfHeader: String?, val csrfToken: String?)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(PATH) { respond(call, request(call)) }
            head(PATH) { respond(call, request(call)) }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val data = withContext(Dispatchers.IO) {
            playback.load(request.file, request.user, request.playerId, request.baseUrl, request.position) +
                mapOf("csrfHeader" to request.csrfHeader, "csrfToken" to request.csrfToken)
        }
        call.response.header("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(data), ContentType.Application.Json)
    }

    companion object { const val PATH = "/rest/videoPlayer" }
}
