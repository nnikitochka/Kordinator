package su.nnedition.kord.api.module.artwork

import io.ktor.server.routing.Route

import io.ktor.server.auth.principal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.request.receiveText
import io.ktor.server.request.receiveChannel
import io.ktor.server.routing.put
import io.ktor.server.routing.get
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.ktor.jsonMapper
import org.airsonic.player.ktor.respondJson
import org.airsonic.player.security.LocalUserPrincipal
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError

fun Route.artworkRoutes(
    context: ApiContext,
) {
    get("/artwork/search") {
        if (call.principal<LocalUserPrincipal>() == null) return@get call.respond(HttpStatusCode.Unauthorized)
        val service = context.artworkSearchService
            ?: return@get call.respondJson(ApiError("unavailable", "Artwork search is not configured"), HttpStatusCode.ServiceUnavailable)
        val artist = call.request.queryParameters["artist"]?.takeIf(String::isNotBlank)
        val album = call.request.queryParameters["album"]?.takeIf(String::isNotBlank)
        if ((artist == null && album == null) || (artist?.length ?: 0) > 200 || (album?.length ?: 0) > 200) {
            return@get call.respondJson(
                ApiError(
                    "invalid_request",
                    "Artist or album is required; each must be at most 200 characters"
                ), HttpStatusCode.BadRequest)
        }
        val matches = withContext(Dispatchers.IO) { service.search(artist, album) }
        call.respondJson(matches)
    }
    put("/library/directories/{id}/artwork/from-search") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            return@put call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
        }
        val searchService = context.artworkSearchService
            ?: return@put call.respondJson(ApiError("unavailable", "Artwork search is not configured"), HttpStatusCode.ServiceUnavailable)
        val managementService = context.artworkManagementService
            ?: return@put call.respondJson(ApiError("unavailable", "Artwork storage is not configured"), HttpStatusCode.ServiceUnavailable)
        val id = call.parameters["id"]?.toIntOrNull()?.takeIf { it > 0 }
            ?: return@put call.respondJson(
                ApiError(
                    "invalid_request",
                    "Directory ID must be a positive number"
                ), HttpStatusCode.BadRequest)
        val selection = runCatching {
            jsonMapper.readValue(call.receiveText(), ArtworkSelectionRequest::class.java)
        }.getOrElse {
            return@put call.respondJson(ApiError("invalid_request", "Artwork selection is required"), HttpStatusCode.BadRequest)
        }
        try {
            val bytes = withContext(Dispatchers.IO) {
                searchService.downloadSelectedArtwork(selection.artist, selection.album, selection.imageUrl)
            }
            val stored = withContext(Dispatchers.IO) { managementService.setDirectoryArtwork(id, bytes) }
            call.respondJson(
                DirectoryArtworkResponse(
                    id,
                    stored.relativePath,
                    stored.contentType,
                    stored.sizeBytes
                )
            )
        } catch (failure: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_artwork", failure.message ?: "Artwork selection is invalid"), HttpStatusCode.BadRequest)
        }
    }
    put("/library/directories/{id}/artwork") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            return@put call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
        }
        val service = context.artworkManagementService
            ?: return@put call.respondJson(ApiError("unavailable", "Artwork storage is not configured"), HttpStatusCode.ServiceUnavailable)
        val id = call.parameters["id"]?.toIntOrNull()?.takeIf { it > 0 }
            ?: return@put call.respondJson(
                ApiError(
                    "invalid_request",
                    "Directory ID must be a positive number"
                ), HttpStatusCode.BadRequest)
        val contentType = call.request.headers[HttpHeaders.ContentType]
        if (contentType != null && !contentType.substringBefore(';').startsWith("image/", ignoreCase = true)) {
            return@put call.respondJson(ApiError("invalid_artwork", "Request body must be an image"), HttpStatusCode.UnsupportedMediaType)
        }
        val bytes = withContext(Dispatchers.IO) { call.receiveChannel().toInputStream().use { it.readNBytes(20 * 1024 * 1024 + 1) } }
        if (bytes.size > 20 * 1024 * 1024) {
            return@put call.respondJson(ApiError("artwork_too_large", "Artwork must not exceed 20 MiB"), HttpStatusCode.PayloadTooLarge)
        }
        try {
            val stored = withContext(Dispatchers.IO) { service.setDirectoryArtwork(id, bytes) }
            call.respondJson(
                DirectoryArtworkResponse(
                    id,
                    stored.relativePath,
                    stored.contentType,
                    stored.sizeBytes
                )
            )
        } catch (failure: IllegalArgumentException) {
            val missingDirectory = failure.message == "Directory does not exist" || failure.message == "Library does not exist"
            call.respondJson(
                ApiError(
                    if (missingDirectory) "not_found" else "invalid_artwork",
                    failure.message ?: "Artwork is invalid"
                ),
                if (missingDirectory) HttpStatusCode.NotFound else HttpStatusCode.BadRequest,
            )
        }
    }
}
