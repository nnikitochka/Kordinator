package su.nnedition.kord.api.module.bookmark

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import su.nnedition.kord.security.LocalUserPrincipal
import su.nnedition.kord.service.StandaloneBookmark
import su.nnedition.kord.service.StandaloneBookmarkService
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.module.track.TrackResponse
import su.nnedition.kord.api.support.respondJson

/** Bearer-scoped resume positions and comments for the standalone Ktor API. */
fun Route.bookmarkRoutes(
    service: StandaloneBookmarkService?,
    objectMapper: ObjectMapper,
    databaseDispatcher: CoroutineDispatcher,
    accountDispatcher: CoroutineDispatcher,
) {
    get("/me/bookmarks") {
        val bookmarkService = service ?: return@get call.respondJson(
            ApiError("unavailable", "Bookmark storage is not configured"), HttpStatusCode.ServiceUnavailable,
        )
        val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val items = withContext(databaseDispatcher) { bookmarkService.list(user.id, user.libraryScope()) }
        call.respondJson(BookmarkPage(items.map(StandaloneBookmark::toResponse), items.size))
    }

    put("/tracks/{id}/bookmark") {
        val bookmarkService = service ?: return@put call.respondJson(
            ApiError("unavailable", "Bookmark storage is not configured"), HttpStatusCode.ServiceUnavailable,
        )
        val user = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
        val trackId = call.parameters["id"]?.toLongOrNull()
        if (trackId == null || trackId < 0) {
            return@put call.respondJson(
                ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest,
            )
        }
        val request = try {
            objectMapper.readValue(call.receiveText(), SetBookmarkRequest::class.java)
        } catch (_: Exception) {
            return@put call.respondJson(
                ApiError("invalid_request", "Expected non-negative positionMillis and optional comment"),
                HttpStatusCode.BadRequest,
            )
        }
        val saved = try {
            withContext(databaseDispatcher) {
                bookmarkService.set(user.id, trackId, request.positionMillis, request.comment, user.libraryScope())
            }
        } catch (failure: IllegalArgumentException) {
            return@put call.respondJson(
                ApiError("invalid_request", failure.message ?: "Bookmark is invalid"), HttpStatusCode.BadRequest,
            )
        }
        if (saved) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
    }

    delete("/tracks/{id}/bookmark") {
        val bookmarkService = service ?: return@delete call.respondJson(
            ApiError("unavailable", "Bookmark storage is not configured"), HttpStatusCode.ServiceUnavailable,
        )
        val user = call.principal<LocalUserPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val trackId = call.parameters["id"]?.toLongOrNull()
        if (trackId == null || trackId < 0) {
            return@delete call.respondJson(
                ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest,
            )
        }
        val deleted = withContext(accountDispatcher) { bookmarkService.delete(user.id, trackId) }
        if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
    }
}

private fun LocalUserPrincipal.libraryScope() = if (role == "ADMIN") null else allowedLibraryIds

private fun StandaloneBookmark.toResponse() = BookmarkResponse(
    track = TrackResponse(
        track.id, track.libraryId, track.relativePath, track.title, track.artist, track.album, track.albumArtist,
        track.durationSeconds, track.sizeBytes, track.modifiedAt?.toString(),
        track.discNumber, track.trackNumber, track.year, track.genre, track.releaseDate?.toString(),
    ),
    positionMillis = positionMillis,
    comment = comment,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)
