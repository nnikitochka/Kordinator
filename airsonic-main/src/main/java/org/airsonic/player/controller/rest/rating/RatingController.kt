package org.airsonic.player.controller.rest.rating

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.RatingService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Response
import kotlin.coroutines.cancellation.CancellationException

class RatingController(
    private val mediaFiles: MediaFileService,
    private val ratings: RatingService,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> RatingRequest) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: RatingRequest) {
        val params = request.parameters
        val rawRating = params["rating"]
        if (rawRating == null) {
            reply(call, params, writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Required param (rating) is missing"))
            return
        }
        val rating = rawRating.toIntOrNull()
        if (rating == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val rawId = params["id"]
        if (rawId == null) {
            reply(call, params, writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Required param (id) is missing"))
            return
        }
        val id = rawId.toIntOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val result = try {
            val mediaFile = mediaFiles.getMediaFile(id)
            if (mediaFile == null) {
                writer.createErrorResponse(ErrorCode.NOT_FOUND, "File not found: $id")
            } else {
                ratings.setRatingForUser(request.username, mediaFile, if (rating == 0) null else rating)
                writer.createResponse(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in rating API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        reply(call, params, result)
    }

    private suspend fun reply(call: ApplicationCall, params: Parameters, result: Response) {
        val serialized = writer.serializeForType(params["f"] ?: "xml", params["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RatingController::class.java)
        @JvmField val PATHS = arrayOf("/rest/setRating", "/rest/setRating.view")
    }
}
