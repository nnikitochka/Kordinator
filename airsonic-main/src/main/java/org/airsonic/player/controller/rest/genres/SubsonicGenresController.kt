package org.airsonic.player.controller.rest.genres

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

/** Ktor routes for the Subsonic genre list. */
class SubsonicGenresController(
    private val genresService: SubsonicGenresService,
    private val jaxbWriter: JAXBWriter,
) {
    fun routes(root: Route, parameters: (ApplicationCall) -> Parameters) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, parameters(call)) }
                post(path) { respond(call, parameters(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, parameters: Parameters) {
        val response = try {
            jaxbWriter.createResponse(true).apply { genres = genresService.getGenres() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in Subsonic genres API", e)
            jaxbWriter.createErrorResponse(ErrorCode.GENERIC, e.message ?: e.javaClass.simpleName)
        }
        val serialized = jaxbWriter.serializeForType(parameters["f"] ?: "xml", parameters["callback"], response)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SubsonicGenresController::class.java)

        @JvmField
        val PATHS = arrayOf("/rest/getGenres", "/rest/getGenres.view")
    }
}
