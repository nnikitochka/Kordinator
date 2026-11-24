package su.nnedition.kord.api.module.user.playback

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LocalUserPrincipal
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.authDispatcher
import su.nnedition.kord.api.module.bookmark.bookmarkRoutes
import su.nnedition.kord.api.module.lyric.lyricsRoutes
import su.nnedition.kord.api.module.rating.ratingRoutes
import su.nnedition.kord.api.module.scrobble.scrobbleRoutes
import su.nnedition.kord.api.scanDispatcher
import su.nnedition.kord.api.support.catalogLibraryScope
import su.nnedition.kord.api.support.jsonMapper
import su.nnedition.kord.api.support.respondJson
import su.nnedition.kord.api.support.toResponse

fun Route.userPlaybackRoutes(
    context: ApiContext,
) {
    get("/me/play-queue") {
        val service = context.playQueueService
        val catalog = context.mediaCatalogService
        if (service == null || catalog == null) {
            call.respondJson(ApiError("unavailable", "Playback queue storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val queue = withContext(authDispatcher) { service.get(user.id) }
        call.respondJson(queue.toResponse(catalog, user.catalogLibraryScope()))
    }
    put("/me/play-queue") {
        val service = context.playQueueService
        val catalog = context.mediaCatalogService
        if (service == null || catalog == null) {
            call.respondJson(ApiError("unavailable", "Playback queue storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@put
        }
        val user = call.principal<LocalUserPrincipal>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)
        val request = try {
            jsonMapper.readValue(call.receiveText(), SavePlayQueueRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(
                ApiError(
                    "invalid_request",
                    "Expected trackIds and optional currentTrackId and positionMillis"
                ), HttpStatusCode.BadRequest)
            return@put
        }
        val queue = try {
            withContext(scanDispatcher) {
                service.save(
                    user.id,
                    request.trackIds,
                    request.currentTrackId,
                    request.positionMillis,
                    user.catalogLibraryScope(),
                )
            }
        } catch (failure: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_queue", failure.message ?: "Queue is invalid"), HttpStatusCode.BadRequest)
            return@put
        }
        call.respondJson(queue.toResponse(catalog, user.catalogLibraryScope()))
    }
    delete("/me/play-queue") {
        val service = context.playQueueService
        if (service == null) {
            call.respondJson(ApiError("unavailable", "Playback queue storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@delete
        }
        val user = call.principal<LocalUserPrincipal>()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        withContext(authDispatcher) { service.clear(user.id) }
        call.respond(HttpStatusCode.NoContent)
    }

    bookmarkRoutes(context.bookmarkService, jsonMapper, scanDispatcher, authDispatcher)

    get("/tracks/{id}/note") {
        val service = context.trackNoteService
        val catalog = context.mediaCatalogService
        if (service == null || catalog == null) {
            call.respondJson(ApiError("unavailable", "Track note storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val trackId = call.parameters["id"]?.toLongOrNull()
        if (trackId == null || trackId < 0) {
            call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return@get
        }
        if (catalog.track(trackId, user.catalogLibraryScope()) == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val note = withContext(authDispatcher) { service.get(user.id, trackId) }
        call.respondJson(TrackNoteResponse(trackId, note?.note, note?.createdAt?.toString(), note?.updatedAt?.toString()))
    }
    put("/tracks/{id}/note") {
        val service = context.trackNoteService
        if (service == null) {
            call.respondJson(ApiError("unavailable", "Track note storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@put
        }
        val user = call.principal<LocalUserPrincipal>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)
        val trackId = call.parameters["id"]?.toLongOrNull()
        if (trackId == null || trackId < 0) {
            call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return@put
        }
        val request = try {
            jsonMapper.readValue(call.receiveText(), SetTrackNoteRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected a non-empty note"), HttpStatusCode.BadRequest)
            return@put
        }
        val saved = try {
            withContext(scanDispatcher) {
                service.set(user.id, trackId, request.note, user.catalogLibraryScope())
            }
        } catch (failure: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_request", failure.message ?: "Note is invalid"), HttpStatusCode.BadRequest)
            return@put
        }
        if (saved) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
    }
    delete("/tracks/{id}/note") {
        val service = context.trackNoteService
        if (service == null) {
            call.respondJson(ApiError("unavailable", "Track note storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@delete
        }
        val user = call.principal<LocalUserPrincipal>()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val trackId = call.parameters["id"]?.toLongOrNull()
        if (trackId == null || trackId < 0) {
            call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return@delete
        }
        val deleted = withContext(scanDispatcher) {
            service.delete(user.id, trackId, user.catalogLibraryScope())
        }
        if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
    }
    ratingRoutes(context.ratingService, context.mediaCatalogService, jsonMapper, scanDispatcher, authDispatcher)
    scrobbleRoutes(context.scrobbleService, context.mediaCatalogService, jsonMapper, scanDispatcher)
    lyricsRoutes(context.lyricsService, authDispatcher)
}
