package org.airsonic.player.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.service.KtorCoreMediaCatalogService
import org.airsonic.player.service.StandaloneRatingService

data class SetTrackRatingRequest(val rating: Int)
data class TrackRatingResponse(val rating: Int?)

/** Per-account track rating routes for the standalone Ktor API. */
class StandaloneRatingRoutes(
    private val service: StandaloneRatingService?,
    private val mediaCatalog: KtorCoreMediaCatalogService?,
    private val objectMapper: ObjectMapper,
    private val databaseDispatcher: CoroutineDispatcher,
    private val accountDispatcher: CoroutineDispatcher,
) {
    fun routes(root: Route) {
        root.get("/tracks/{id}/rating") {
            val ratingService = service
            val catalog = mediaCatalog
            if (ratingService == null || catalog == null) {
                return@get call.respondJson(ApiError("unavailable", "Rating storage is not configured"), HttpStatusCode.ServiceUnavailable)
            }
            val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val trackId = call.parameters["id"]?.toLongOrNull()
            if (trackId == null || trackId < 0) {
                return@get call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
            }
            if (withContext(databaseDispatcher) { catalog.track(trackId, user.libraryScope()) } == null) {
                return@get call.respond(HttpStatusCode.NotFound)
            }
            val rating = withContext(accountDispatcher) { ratingService.get(user.id, trackId) }
            call.respondJson(TrackRatingResponse(rating))
        }

        root.put("/tracks/{id}/rating") {
            val ratingService = service ?: return@put call.respondJson(
                ApiError("unavailable", "Rating storage is not configured"), HttpStatusCode.ServiceUnavailable,
            )
            val user = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
            val trackId = call.parameters["id"]?.toLongOrNull()
            if (trackId == null || trackId < 0) {
                return@put call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
            }
            val request = try {
                objectMapper.readValue(call.receiveText(), SetTrackRatingRequest::class.java)
            } catch (_: Exception) {
                return@put call.respondJson(ApiError("invalid_request", "Expected rating from 0 to 5"), HttpStatusCode.BadRequest)
            }
            val saved = try {
                withContext(databaseDispatcher) {
                    ratingService.set(user.id, trackId, request.rating, user.libraryScope())
                }
            } catch (failure: IllegalArgumentException) {
                return@put call.respondJson(ApiError("invalid_request", failure.message ?: "Rating is invalid"), HttpStatusCode.BadRequest)
            }
            if (saved) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
        }
    }
}

private fun LocalUserPrincipal.libraryScope() = if (role == "ADMIN") null else allowedLibraryIds
