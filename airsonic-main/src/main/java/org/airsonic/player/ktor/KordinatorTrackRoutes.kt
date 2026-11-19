package org.airsonic.player.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.SecurityService

data class KordinatorTrackResponse(
    val id: Int,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val year: Int?,
    val durationSeconds: Int,
    val fileSize: Long?,
    val format: String?,
)

fun interface KordinatorTrackLookup {
    fun findAccessibleTrack(id: Int, username: String): KordinatorTrackResponse?
}

class MediaFileTrackLookup(
    private val mediaFileService: MediaFileService,
    private val securityService: SecurityService,
) : KordinatorTrackLookup {
    override fun findAccessibleTrack(id: Int, username: String): KordinatorTrackResponse? {
        val mediaFile = mediaFileService.getMediaFile(id) ?: return null
        if (mediaFile.mediaType != MediaFile.MediaType.MUSIC && mediaFile.mediaType != MediaFile.MediaType.AUDIOBOOK) {
            return null
        }
        if (!securityService.isFolderAccessAllowed(mediaFile, username)) return null

        return KordinatorTrackResponse(
            id = requireNotNull(mediaFile.id),
            title = mediaFile.title,
            artist = mediaFile.artist,
            album = mediaFile.albumName,
            albumArtist = mediaFile.albumArtist,
            discNumber = mediaFile.discNumber,
            trackNumber = mediaFile.trackNumber,
            year = mediaFile.year,
            durationSeconds = mediaFile.duration?.toInt() ?: 0,
            fileSize = mediaFile.fileSize,
            format = mediaFile.format,
        )
    }
}

fun Route.kordinatorTrackRoutes(
    trackLookup: KordinatorTrackLookup,
    currentUsername: (ApplicationCall) -> String?,
    objectMapper: ObjectMapper,
) {
    get("/api/v1/tracks/{id}") {
        val username = currentUsername(call)
        if (username.isNullOrBlank()) {
            call.respond(HttpStatusCode.Unauthorized)
            return@get
        }

        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null || id <= 0) {
            call.respondText(
                objectMapper.writeValueAsString(ApiError("invalid_request", "Track ID must be a positive number")),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
            return@get
        }

        val track = trackLookup.findAccessibleTrack(id, username)
        if (track == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respondText(objectMapper.writeValueAsString(track), ContentType.Application.Json)
        }
    }
}
