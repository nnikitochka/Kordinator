package org.airsonic.player.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.service.MediaFolderService

data class KordinatorArtistSummary(
    val id: Int,
    val name: String,
    val albumCount: Int,
)

data class KordinatorArtistAlbum(
    val id: Int,
    val name: String,
    val artist: String,
    val year: Int?,
    val genre: String?,
    val trackCount: Int,
    val durationSeconds: Int,
)

data class KordinatorArtistDetails(
    val id: Int,
    val name: String,
    val albumCount: Int,
    val albums: List<KordinatorArtistAlbum>,
)

class KordinatorArtistRoutes(
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val mediaFolderService: MediaFolderService,
) {
    fun routes(
        root: Route,
        currentUsername: (ApplicationCall) -> String?,
        objectMapper: ObjectMapper,
    ) {
        root.get("/api/v1/artists/{id}") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null || id <= 0) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Artist ID must be a positive number")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@get
            }

            val folders = mediaFolderService.getMusicFoldersForUser(username)
            val visibleFolderIds = MusicFolder.toIdList(folders).toSet()
            val artist = artistDao.getArtist(id)
            if (artist == null || artist.folderId !in visibleFolderIds) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val albums = albumDao.getAlbumsForArtist(artist.name, folders).map {
                KordinatorArtistAlbum(
                    id = it.id,
                    name = it.name,
                    artist = it.artist,
                    year = it.year,
                    genre = it.genre,
                    trackCount = it.songCount,
                    durationSeconds = it.duration.toInt(),
                )
            }
            val details = KordinatorArtistDetails(artist.id, artist.name!!, albums.size, albums)
            call.respondText(objectMapper.writeValueAsString(details), ContentType.Application.Json)
        }

        root.get("/api/v1/artists") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val offsetValue = call.request.queryParameters["offset"]
            val limitValue = call.request.queryParameters["limit"]
            val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
            val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 50 else null
            if (offset == null || limit == null || offset < 0 || limit !in 1..200) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Offset must be non-negative and limit must be between 1 and 200")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@get
            }

            val folders = mediaFolderService.getMusicFoldersForUser(username)
            val artists = artistDao.getAlphabetialArtists(offset, limit, folders).map {
                KordinatorArtistSummary(it.id, it.name!!, it.albumCount)
            }
            call.respondText(objectMapper.writeValueAsString(artists), ContentType.Application.Json)
        }
    }
}
