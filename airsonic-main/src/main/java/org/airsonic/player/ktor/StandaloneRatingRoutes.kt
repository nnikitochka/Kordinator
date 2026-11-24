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
import su.nnedition.kord.api.ApiError

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

        root.get("/albums/{id}/rating") {
            val ratingService = service
                ?: return@get call.respondJson(ApiError("unavailable", "Rating storage is not configured"), HttpStatusCode.ServiceUnavailable)
            val catalog = mediaCatalog
                ?: return@get call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
            val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val selector = call.parameters["id"]?.decodeCatalogId()
                ?: return@get call.respondJson(ApiError("invalid_request", "Album ID is invalid"), HttpStatusCode.BadRequest)
            val scope = user.libraryScope()
            if (withContext(databaseDispatcher) { catalog.album(selector.first, selector.second, scope) } == null) {
                return@get call.respond(HttpStatusCode.NotFound)
            }
            val rating = withContext(accountDispatcher) { ratingService.getAlbum(user.id, selector.first, selector.second) }
            call.respondJson(AlbumRatingResponse(rating))
        }

        root.put("/albums/{id}/rating") {
            val ratingService = service
                ?: return@put call.respondJson(ApiError("unavailable", "Rating storage is not configured"), HttpStatusCode.ServiceUnavailable)
            val user = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
            val selector = call.parameters["id"]?.decodeCatalogId()
                ?: return@put call.respondJson(ApiError("invalid_request", "Album ID is invalid"), HttpStatusCode.BadRequest)
            val request = try {
                objectMapper.readValue(call.receiveText(), SetTrackRatingRequest::class.java)
            } catch (_: Exception) {
                return@put call.respondJson(ApiError("invalid_request", "Expected rating from 0 to 5"), HttpStatusCode.BadRequest)
            }
            val saved = try {
                withContext(databaseDispatcher) {
                    ratingService.setAlbum(user.id, selector.first, selector.second, request.rating, user.libraryScope())
                }
            } catch (failure: IllegalArgumentException) {
                return@put call.respondJson(ApiError("invalid_request", failure.message ?: "Rating is invalid"), HttpStatusCode.BadRequest)
            }
            if (saved) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
        }
    }
}

private fun LocalUserPrincipal.libraryScope() = if (role == "ADMIN") null else allowedLibraryIds

data class AlbumRatingResponse(val rating: Int?)

private fun String.decodeCatalogId(): Pair<Long, String>? = try {
    val separator = indexOf('.')
    if (separator < 1 || separator == lastIndex) return null
    val libraryId = substring(0, separator).toLongOrNull()?.takeIf { it >= 0 } ?: return null
    val name = java.util.Base64.getUrlDecoder().decode(substring(separator + 1)).toString(Charsets.UTF_8)
    if (name.isBlank()) null else libraryId to name
} catch (_: IllegalArgumentException) {
    null
}
