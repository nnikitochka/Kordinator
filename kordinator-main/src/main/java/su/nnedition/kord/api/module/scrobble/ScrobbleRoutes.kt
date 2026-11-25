package su.nnedition.kord.api.module.scrobble

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import su.nnedition.kord.security.LocalUserPrincipal
import su.nnedition.kord.service.KtorCoreMediaCatalogService
import su.nnedition.kord.service.StandaloneScrobbleService
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.support.respondJson

fun Route.scrobbleRoutes(
    service: StandaloneScrobbleService?,
    mediaCatalog: KtorCoreMediaCatalogService?,
    objectMapper: ObjectMapper,
    dispatcher: CoroutineDispatcher,
) {
    post("/tracks/{id}/scrobble") {
        val scrobbleService = service
        val catalog = mediaCatalog
        if (scrobbleService == null || catalog == null) {
            return@post call.respondJson(
                ApiError("unavailable", "Scrobble processing is not configured"), HttpStatusCode.ServiceUnavailable,
            )
        }
        val user = call.principal<LocalUserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val trackId = call.parameters["id"]?.toLongOrNull()
        if (trackId == null || trackId < 0) {
            return@post call.respondJson(
                ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest,
            )
        }
        val request = try {
            objectMapper.readValue(call.receiveText(), ScrobbleTrackRequest::class.java)
        } catch (_: Exception) {
            return@post call.respondJson(
                ApiError("invalid_request", "Expected optional ISO-8601 time and submission flag"),
                HttpStatusCode.BadRequest,
            )
        }
        val time = try {
            request.time?.let(Instant::parse)
        } catch (_: Exception) {
            return@post call.respondJson(ApiError("invalid_request", "Time must be ISO-8601"), HttpStatusCode.BadRequest)
        }
        if (withContext(dispatcher) { catalog.track(trackId, user.libraryScope()) } == null) {
            return@post call.respond(HttpStatusCode.NotFound)
        }
        val recorded = withContext(dispatcher) {
            scrobbleService.scrobble(user.username, trackId, time, request.submission)
        }
        if (recorded) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
    }
}

private fun LocalUserPrincipal.libraryScope() = if (role == "ADMIN") null else allowedLibraryIds
