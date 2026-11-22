package org.airsonic.player.ktor.album

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.ktor.ApiError
import org.airsonic.player.ktor.KordinatorTrackResponse
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SecurityService

class AlbumRoutes(
    private val mediaFileService: MediaFileService,
    private val mediaFolderService: MediaFolderService,
    private val securityService: SecurityService,
) {
    fun routes(
        root: Route,
        currentUsername: (ApplicationCall) -> String?,
        objectMapper: ObjectMapper,
    ) {
        root.get("/api/v1/albums/{id}") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null || id <= 0) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Album ID must be a positive number")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@get
            }

            val album = mediaFileService.getMediaFile(id)
            if (album == null || album.mediaType != MediaFile.MediaType.ALBUM ||
                !securityService.isFolderAccessAllowed(album, username)
            ) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val tracks = mediaFileService.getChildrenOf(album, true, false, true)
                .asSequence()
                .filter { it.isAudio }
                .filter { securityService.isFolderAccessAllowed(it, username) }
                .map { it.toTrack() }
                .toList()
            val details = AlbumDetails(
                id = requireNotNull(album.id),
                name = album.albumName,
                artist = album.artist,
                year = album.year,
                genre = album.genre,
                trackCount = tracks.size,
                durationSeconds = tracks.sumOf { it.durationSeconds },
                tracks = tracks,
            )
            call.respondText(objectMapper.writeValueAsString(details), ContentType.Application.Json)
        }

        root.get("/api/v1/albums") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val offsetValue = call.request.queryParameters["offset"]
            val limitValue = call.request.queryParameters["limit"]
            val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
            val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 20 else null
            if (offset == null || limit == null) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Offset and limit must be integers")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@get
            }
            val byArtist = when (call.request.queryParameters["sort"] ?: "artist") {
                "name" -> false
                "artist" -> true
                else -> {
                    call.respondText(
                        objectMapper.writeValueAsString(ApiError("invalid_request", "Sort must be 'name' or 'artist'")),
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@get
                }
            }
            if (offset < 0 || limit !in 1..100) {
                call.respondText(
                    objectMapper.writeValueAsString(
                        ApiError(
                            "invalid_request",
                            "Offset must be non-negative and limit must be between 1 and 100"
                        )
                    ),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@get
            }

            val musicFolders = mediaFolderService.getMusicFoldersForUser(username)
            val albums = mediaFileService.getAlphabeticalAlbums(offset, limit, byArtist, musicFolders)
                .asSequence()
                .filter { it.mediaType == MediaFile.MediaType.ALBUM }
                .filter { securityService.isFolderAccessAllowed(it, username) }
                .map { it.toAlbumSummary() }
                .toList()

            call.respondText(objectMapper.writeValueAsString(albums), ContentType.Application.Json)
        }
    }
}

private fun MediaFile.toAlbumSummary() = AlbumSummary(
    id = requireNotNull(id),
    name = albumName,
    artist = artist,
    year = year,
    genre = genre,
    durationSeconds = duration?.toInt() ?: 0,
)

private fun MediaFile.toTrack() = KordinatorTrackResponse(
    id = requireNotNull(id),
    title = title,
    artist = artist,
    album = albumName,
    albumArtist = albumArtist,
    discNumber = discNumber,
    trackNumber = trackNumber,
    year = year,
    durationSeconds = duration?.toInt() ?: 0,
    fileSize = fileSize,
    format = format,
)
