package org.airsonic.player.ktor

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
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.service.StandaloneVideoBookmark
import org.airsonic.player.service.StandaloneVideoBookmarkService
import su.nnedition.kord.api.ApiError

data class VideoBookmarkPage(val items: List<VideoBookmarkResponse>, val count: Int)
data class VideoBookmarkResponse(
    val videoId: Int,
    val positionMillis: Long,
    val comment: String?,
    val createdAt: String,
    val updatedAt: String,
)
data class SetVideoBookmarkRequest(val positionMillis: Long, val comment: String? = null)

/** Bearer-scoped video resume bookmarks. */
class StandaloneVideoBookmarkRoutes(
    private val service: StandaloneVideoBookmarkService?,
    private val objectMapper: ObjectMapper,
    private val databaseDispatcher: CoroutineDispatcher,
) {
    fun routes(root: Route) {
        root.get("/me/video-bookmarks") {
            val bookmarkService = service ?: return@get call.respondJson(
                ApiError("unavailable", "Video bookmark storage is not configured"), HttpStatusCode.ServiceUnavailable,
            )
            val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val items = withContext(databaseDispatcher) { bookmarkService.list(user.id, user.libraryScope()) }
            call.respondJson(VideoBookmarkPage(items.map(StandaloneVideoBookmark::toResponse), items.size))
        }

        root.put("/videos/{id}/bookmark") {
            val bookmarkService = service ?: return@put call.respondJson(
                ApiError("unavailable", "Video bookmark storage is not configured"), HttpStatusCode.ServiceUnavailable,
            )
            val user = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
            val videoId = call.parameters["id"]?.toIntOrNull()?.takeIf { it >= 0 }
                ?: return@put call.respondJson(ApiError("invalid_request", "Video ID must be a non-negative integer"), HttpStatusCode.BadRequest)
            val request = try {
                objectMapper.readValue(call.receiveText(), SetVideoBookmarkRequest::class.java)
            } catch (_: Exception) {
                return@put call.respondJson(
                    ApiError("invalid_request", "Expected non-negative positionMillis and optional comment"),
                    HttpStatusCode.BadRequest,
                )
            }
            val saved = try {
                withContext(databaseDispatcher) {
                    bookmarkService.set(user.id, videoId, request.positionMillis, request.comment, user.libraryScope())
                }
            } catch (failure: IllegalArgumentException) {
                return@put call.respondJson(
                    ApiError("invalid_request", failure.message ?: "Bookmark is invalid"), HttpStatusCode.BadRequest,
                )
            }
            if (saved) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
        }

        root.delete("/videos/{id}/bookmark") {
            val bookmarkService = service ?: return@delete call.respondJson(
                ApiError("unavailable", "Video bookmark storage is not configured"), HttpStatusCode.ServiceUnavailable,
            )
            val user = call.principal<LocalUserPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
            val videoId = call.parameters["id"]?.toIntOrNull()?.takeIf { it >= 0 }
                ?: return@delete call.respondJson(
                    ApiError(
                        "invalid_request",
                        "Video ID must be a non-negative integer"
                    ), HttpStatusCode.BadRequest)
            val deleted = withContext(databaseDispatcher) { bookmarkService.delete(user.id, videoId, user.libraryScope()) }
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
        }
    }
}

private fun LocalUserPrincipal.libraryScope(): Set<Long>? = if (role == "ADMIN") null else allowedLibraryIds

private fun StandaloneVideoBookmark.toResponse() = VideoBookmarkResponse(
    videoId, positionMillis, comment, createdAt.toString(), updatedAt.toString(),
)
