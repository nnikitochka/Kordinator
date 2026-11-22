package org.airsonic.player.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.domain.Album
import org.airsonic.player.domain.Artist
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.ktor.album.AlbumSummary
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SecurityService

data class KordinatorFavoriteTracksResponse(
    val offset: Int,
    val limit: Int,
    val tracks: List<KordinatorTrackResponse>,
)

data class KordinatorFavoriteAlbumsResponse(
    val offset: Int,
    val limit: Int,
    val albums: List<AlbumSummary>,
)

data class KordinatorFavoriteArtistsResponse(
    val offset: Int,
    val limit: Int,
    val artists: List<KordinatorArtistSummary>,
)

class KordinatorFavoriteRoutes(
    private val mediaFileDao: MediaFileDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val mediaFileService: MediaFileService,
    private val mediaFolderService: MediaFolderService,
    private val securityService: SecurityService,
) {
    fun routes(
        root: Route,
        currentUsername: (ApplicationCall) -> String?,
        objectMapper: ObjectMapper,
    ) {
        root.get("/api/v1/favorites/tracks") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val offsetValue = call.request.queryParameters["offset"]
            val limitValue = call.request.queryParameters["limit"]
            val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
            val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 50 else null
            if (offset == null || limit == null || offset < 0 || limit !in 1..100) {
                invalidRequest(call, objectMapper, "Offset must be non-negative and limit must be between 1 and 100")
                return@get
            }

            val folders = mediaFolderService.getMusicFoldersForUser(username)
            val tracks = mediaFileDao.getStarredFiles(offset, limit, username, folders)
                .asSequence()
                .filter { it.isAudio }
                .filter { securityService.isFolderAccessAllowed(it, username) }
                .map { it.toKordinatorTrack() }
                .toList()
            call.respondText(
                objectMapper.writeValueAsString(KordinatorFavoriteTracksResponse(offset, limit, tracks)),
                ContentType.Application.Json,
            )
        }

        root.get("/api/v1/favorites/albums") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }
            val page = page(call, objectMapper) ?: return@get
            val albums = albumDao.getStarredAlbums(
                page.first,
                page.second,
                username,
                mediaFolderService.getMusicFoldersForUser(username),
            ).map { it.toKordinatorAlbum() }
            call.respondText(
                objectMapper.writeValueAsString(KordinatorFavoriteAlbumsResponse(page.first, page.second, albums)),
                ContentType.Application.Json,
            )
        }

        root.get("/api/v1/favorites/artists") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }
            val page = page(call, objectMapper) ?: return@get
            val artists = artistDao.getStarredArtists(
                page.first,
                page.second,
                username,
                mediaFolderService.getMusicFoldersForUser(username),
            ).map { it.toKordinatorArtist() }
            call.respondText(
                objectMapper.writeValueAsString(KordinatorFavoriteArtistsResponse(page.first, page.second, artists)),
                ContentType.Application.Json,
            )
        }

        root.put("/api/v1/tracks/{id}/favorite") {
            updateFavorite(call, currentUsername(call), objectMapper, true)
        }

        root.delete("/api/v1/tracks/{id}/favorite") {
            updateFavorite(call, currentUsername(call), objectMapper, false)
        }

        root.put("/api/v1/albums/{id}/favorite") {
            updateAlbumFavorite(call, currentUsername(call), objectMapper, true)
        }
        root.delete("/api/v1/albums/{id}/favorite") {
            updateAlbumFavorite(call, currentUsername(call), objectMapper, false)
        }
        root.put("/api/v1/artists/{id}/favorite") {
            updateArtistFavorite(call, currentUsername(call), objectMapper, true)
        }
        root.delete("/api/v1/artists/{id}/favorite") {
            updateArtistFavorite(call, currentUsername(call), objectMapper, false)
        }
    }

    private suspend fun updateFavorite(
        call: ApplicationCall,
        username: String?,
        objectMapper: ObjectMapper,
        favorite: Boolean,
    ) {
        if (username.isNullOrBlank()) {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null || id <= 0) {
            invalidRequest(call, objectMapper, "Track ID must be a positive number")
            return
        }
        val track = mediaFileService.getMediaFile(id)
        if (track == null || (track.mediaType != MediaFile.MediaType.MUSIC &&
                track.mediaType != MediaFile.MediaType.AUDIOBOOK) ||
            !securityService.isFolderAccessAllowed(track, username)
        ) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        if (favorite) mediaFileDao.starMediaFile(id, username) else mediaFileDao.unstarMediaFile(id, username)
        call.respond(HttpStatusCode.NoContent)
    }

    private suspend fun updateAlbumFavorite(
        call: ApplicationCall,
        username: String?,
        objectMapper: ObjectMapper,
        favorite: Boolean,
    ) {
        if (username.isNullOrBlank()) {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null || id <= 0) {
            invalidRequest(call, objectMapper, "Album ID must be a positive number")
            return
        }
        val album = albumDao.getAlbum(id)
        if (album == null || !hasFolderAccess(username, album.folderId)) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        if (favorite) albumDao.starAlbum(id, username) else albumDao.unstarAlbum(id, username)
        call.respond(HttpStatusCode.NoContent)
    }

    private suspend fun updateArtistFavorite(
        call: ApplicationCall,
        username: String?,
        objectMapper: ObjectMapper,
        favorite: Boolean,
    ) {
        if (username.isNullOrBlank()) {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null || id <= 0) {
            invalidRequest(call, objectMapper, "Artist ID must be a positive number")
            return
        }
        val artist = artistDao.getArtist(id)
        if (artist == null || !hasFolderAccess(username, artist.folderId)) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        if (favorite) artistDao.starArtist(id, username) else artistDao.unstarArtist(id, username)
        call.respond(HttpStatusCode.NoContent)
    }

    private suspend fun page(call: ApplicationCall, objectMapper: ObjectMapper): Pair<Int, Int>? {
        val offsetValue = call.request.queryParameters["offset"]
        val limitValue = call.request.queryParameters["limit"]
        val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
        val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 50 else null
        if (offset == null || limit == null || offset < 0 || limit !in 1..100) {
            invalidRequest(call, objectMapper, "Offset must be non-negative and limit must be between 1 and 100")
            return null
        }
        return offset to limit
    }

    private fun hasFolderAccess(username: String, folderId: Int?): Boolean =
        folderId != null && mediaFolderService.getMusicFoldersForUser(username).any { it.id == folderId }

    private suspend fun invalidRequest(call: ApplicationCall, objectMapper: ObjectMapper, message: String) {
        call.respondText(
            objectMapper.writeValueAsString(ApiError("invalid_request", message)),
            ContentType.Application.Json,
            HttpStatusCode.BadRequest,
        )
    }

    private fun MediaFile.toKordinatorTrack() = KordinatorTrackResponse(
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

    private fun Album.toKordinatorAlbum() = AlbumSummary(
        id = id,
        name = name,
        artist = artist,
        year = year,
        genre = genre,
        durationSeconds = duration.toInt(),
    )

    private fun Artist.toKordinatorArtist() = KordinatorArtistSummary(
        id = id,
        name = name,
        albumCount = albumCount,
    )
}
