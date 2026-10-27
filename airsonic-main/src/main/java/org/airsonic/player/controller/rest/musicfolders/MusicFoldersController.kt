package org.airsonic.player.controller.rest.musicfolders

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

/** Ktor routes for the Subsonic music-folders API. */
class MusicFoldersController(
    private val musicFoldersService: MusicFoldersService,
    private val jaxbWriter: JAXBWriter,
) {
    /** The temporary servlet adapter supplies the authenticated user and merged query/form parameters. */
    data class Request(val username: String, val parameters: Parameters)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val response = try {
            jaxbWriter.createResponse(true).apply {
                musicFolders = musicFoldersService.getMusicFolders(request.username)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in Subsonic music-folders API", e)
            jaxbWriter.createErrorResponse(ErrorCode.GENERIC, e.message ?: e.javaClass.simpleName)
        }
        val serialized = jaxbWriter.serializeForType(
            request.parameters["f"] ?: "xml", request.parameters["callback"], response
        )
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MusicFoldersController::class.java)

        @JvmField
        val PATHS = arrayOf("/rest/getMusicFolders", "/rest/getMusicFolders.view")
    }
}
