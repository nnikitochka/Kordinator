package org.airsonic.player.controller.rest.hls

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.User
import org.airsonic.player.security.CallerContext
import org.airsonic.player.security.JWTAuthenticationToken
import org.airsonic.player.service.hls.HlsSegmentService
import org.springframework.security.core.Authentication
import java.util.concurrent.TimeoutException

class HlsSegmentRestController(private val segments: HlsSegmentService) {
    data class Request(val auth: Authentication, val user: User, val callerContext: CallerContext, val player: Player, val id: Int,
                       val playerId: String, val index: Int, val bitRate: Int, val size: String,
                       val duration: Int, val audioTrack: Int?, val enforceFolderAccess: Boolean = true)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            (PATHS + WEBSITE_PATHS).forEach { path ->
                get(path) { respond(call, request(call), false) }
                head(path) { respond(call, request(call), true) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request, head: Boolean) {
        val result = try {
            withContext(Dispatchers.IO) {
                segments.getSegment(request.auth is JWTAuthenticationToken, request.user, request.callerContext, request.player, request.id, request.playerId,
                    request.index, request.bitRate, request.size, request.duration, request.audioTrack, request.enforceFolderAccess, !head)
            }
        } catch (e: TimeoutException) {
            call.respond(HttpStatusCode.ServiceUnavailable)
            return
        }
        val resource = result
        call.response.header("Access-Control-Allow-Origin", "*")
        call.response.header("Content-Disposition", "inline; filename=\"f.ts\"")
        val contentType = ContentType.parse("video/MP2T")
        if (head) {
            resource.inputStream.close()
            call.respond(object : OutgoingContent.NoContent() {
                override val contentType = contentType
                override val contentLength = resource.contentLength()
            })
        } else {
            call.respondOutputStream(contentType, contentLength = resource.contentLength()) {
                resource.inputStream.use { it.copyTo(this) }
            }
        }
    }

    companion object {
        @JvmField val WEBSITE_PATHS = arrayOf("/hls/segment/{tail...}", "/ext/hls/segment/{tail...}")
        @JvmField val PATHS = arrayOf("/ext/rest-hls/segment/segment.ts", "/rest/hls/segment/segment.ts", "/rest/hls.view/segment/segment.ts")
    }
}
