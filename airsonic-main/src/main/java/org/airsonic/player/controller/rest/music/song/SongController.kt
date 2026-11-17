package org.airsonic.player.controller.rest.music.song

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.SecurityService
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

class SongController(
    private val mediaFileDao: MediaFileDao,
    private val security: SecurityService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> SongRequest) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: SongRequest) {
        val params = request.parameters
        val rawId = params["id"]
        val id = rawId?.toIntOrNull()
        if (rawId != null && id == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val result = try {
            if (id == null) {
                writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Required param (id) is missing")
            } else {
                val song = mediaFileDao.getMediaFile(id)
                when {
                    song == null || song.isDirectory -> writer.createErrorResponse(ErrorCode.NOT_FOUND, "Song not found.")
                    !security.isFolderAccessAllowed(song, request.callerContext) -> writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Access denied")
                    else -> writer.createResponse(true).apply { this.song = mapper.child(request.player, song, request.username) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in song API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        val serialized = writer.serializeForType(params["f"] ?: "xml", params["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SongController::class.java)
        @JvmField val PATHS = arrayOf("/rest/getSong", "/rest/getSong.view")
    }
}
