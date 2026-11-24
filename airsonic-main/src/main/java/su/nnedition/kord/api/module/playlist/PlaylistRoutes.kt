package su.nnedition.kord.api.module.playlist

import io.ktor.server.routing.Route

import io.ktor.http.ContentType
import io.ktor.server.auth.principal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.request.receiveText
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import kotlinx.coroutines.withContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.ApiContext
import org.airsonic.player.ktor.catalogLibraryScope
import org.airsonic.player.ktor.jsonMapper
import org.airsonic.player.ktor.respondJson
import su.nnedition.kord.api.scanDispatcher
import org.airsonic.player.ktor.toResponse
import org.airsonic.player.security.LocalUserPrincipal

fun Route.playlistRoutes(
    context: ApiContext,
) {
    if (context.playlistService != null && context.mediaCatalogService != null) {
        playlistImportRoutes(context.playlistService, context.mediaCatalogService)
    }
    get("/playlists") {
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        if (context.playlistService == null) {
            call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val accessibleLibraries = user.catalogLibraryScope()
        val playlists = withContext(scanDispatcher) { context.playlistService.list(user.id, accessibleLibraries) }
            .map {
                PlaylistSummaryResponse(
                    it.id, it.name, it.createdAt.toString(), it.updatedAt.toString(),
                    it.trackCount, it.visibility, it.description,
                )
            }
        call.respondJson(playlists)
    }
    post("/playlists") {
        val user = call.principal<LocalUserPrincipal>()
            ?: return@post call.respond(HttpStatusCode.Unauthorized)
        if (context.playlistService == null) {
            call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@post
        }
        val request = try {
            jsonMapper.readValue(call.receiveText(), SavePlaylistRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected name and optional trackIds"), HttpStatusCode.BadRequest)
            return@post
        }
        try {
            val playlist = withContext(scanDispatcher) {
                context.playlistService.create(
                    user.id, request.name, request.trackIds, user.catalogLibraryScope(), request.visibility, request.description,
                )
            }
            call.respondJson(playlist.toResponse(), HttpStatusCode.Created)
        } catch (_: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_playlist", "Playlist name or track IDs are invalid"), HttpStatusCode.BadRequest)
        }
    }
    get("/playlists/{id}/export") {
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val service = context.playlistService
        if (service == null) {
            call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val playlistId = call.parameters["id"]?.toLongOrNull()
        if (playlistId == null || playlistId < 0) {
            call.respondJson(ApiError("invalid_request", "Playlist ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return@get
        }
        val playlist = withContext(scanDispatcher) {
            service.get(user.id, playlistId, user.catalogLibraryScope())
        }
        if (playlist == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val fileName = playlist.name
            .map { if (it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_') it else '_' }
            .joinToString("").trim('_').ifEmpty { "playlist" } + ".m3u8"
        val content = buildString {
            appendLine("#EXTM3U")
            playlist.tracks.forEach { track ->
                val label = listOfNotNull(track.artist?.takeIf(String::isNotBlank), track.title)
                    .joinToString(" - ").replace('\r', ' ').replace('\n', ' ')
                appendLine("#EXTINF:${track.durationSeconds},$label")
                appendLine(track.relativePath.replace('\\', '/'))
            }
        }
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"$fileName\"")
        call.respondText(content, ContentType.parse("application/vnd.apple.mpegurl"))
    }
    get("/playlists/{id}/readers") {
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val service = context.playlistService
            ?: return@get call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
        val playlistId = call.parameters["id"]?.toLongOrNull()?.takeIf { it >= 0 }
            ?: return@get call.respondJson(ApiError("invalid_request", "Playlist ID must be non-negative"), HttpStatusCode.BadRequest)
        val readers = withContext(scanDispatcher) { service.readers(user.id, playlistId) }
        if (readers == null) call.respond(HttpStatusCode.NotFound) else call.respondJson(
            PlaylistReadersResponse(
                readers
            )
        )
    }
    put("/playlists/{id}/readers") {
        val user = call.principal<LocalUserPrincipal>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)
        val service = context.playlistService
            ?: return@put call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
        val playlistId = call.parameters["id"]?.toLongOrNull()?.takeIf { it >= 0 }
            ?: return@put call.respondJson(ApiError("invalid_request", "Playlist ID must be non-negative"), HttpStatusCode.BadRequest)
        val request = try {
            jsonMapper.readValue(call.receiveText(), SetPlaylistReadersRequest::class.java)
        } catch (_: Exception) {
            return@put call.respondJson(ApiError("invalid_request", "Expected userIds"), HttpStatusCode.BadRequest)
        }
        try {
            val readers = withContext(scanDispatcher) { service.setReaders(user.id, playlistId, request.userIds) }
            if (readers == null) call.respond(HttpStatusCode.NotFound) else call.respondJson(
                PlaylistReadersResponse(
                    readers
                )
            )
        } catch (_: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_readers", "Reader account IDs are invalid"), HttpStatusCode.BadRequest)
        }
    }
    get("/playlists/{id}") {
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val service = context.playlistService
        if (service == null) {
            call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val playlistId = call.parameters["id"]?.toLongOrNull()
        if (playlistId == null || playlistId < 0) {
            call.respondJson(ApiError("invalid_request", "Playlist ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return@get
        }
        val playlist = withContext(scanDispatcher) {
            service.get(user.id, playlistId, user.catalogLibraryScope())
        }
        if (playlist == null) call.respond(HttpStatusCode.NotFound) else call.respondJson(playlist.toResponse())
    }
    put("/playlists/{id}") {
        val user = call.principal<LocalUserPrincipal>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)
        val playlistId = call.parameters["id"]?.toLongOrNull()
        if (playlistId == null || playlistId < 0) {
            call.respondJson(ApiError("invalid_request", "Playlist ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return@put
        }
        val service = context.playlistService
        if (service == null) {
            call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@put
        }
        val request = try {
            jsonMapper.readValue(call.receiveText(), SavePlaylistRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected name and trackIds"), HttpStatusCode.BadRequest)
            return@put
        }
        try {
            val playlist = withContext(scanDispatcher) {
                service.update(
                    user.id, playlistId, request.name, request.trackIds, user.catalogLibraryScope(),
                    request.visibility, request.description,
                )
            }
            if (playlist == null) call.respond(HttpStatusCode.NotFound) else call.respondJson(playlist.toResponse())
        } catch (_: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_playlist", "Playlist name or track IDs are invalid"), HttpStatusCode.BadRequest)
        }
    }
    delete("/playlists/{id}") {
        val user = call.principal<LocalUserPrincipal>()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val service = context.playlistService
        if (service == null) {
            call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@delete
        }
        val playlistId = call.parameters["id"]?.toLongOrNull()
        if (playlistId == null || playlistId < 0) {
            call.respondJson(ApiError("invalid_request", "Playlist ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return@delete
        }
        val deleted = withContext(scanDispatcher) { service.delete(user.id, playlistId) }
        if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
    }
}
