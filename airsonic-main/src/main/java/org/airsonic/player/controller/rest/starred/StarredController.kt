package org.airsonic.player.controller.rest.starred

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.MediaFolderService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.AlbumID3
import org.subsonic.restapi.ArtistID3
import org.subsonic.restapi.Response
import org.subsonic.restapi.Starred
import org.subsonic.restapi.Starred2
import kotlin.coroutines.cancellation.CancellationException

class StarredController(
    private val mediaFiles: MediaFileDao,
    private val albums: AlbumDao,
    private val artists: ArtistDao,
    private val folders: MediaFolderService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> StarredRequest) {
        with(root) {
            PATHS.forEach { path ->
                val operation = path.substringAfterLast('/').removeSuffix(".view")
                get(path) { respond(call, request(call), operation) }
                post(path) { respond(call, request(call), operation) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: StarredRequest, operation: String) {
        val result = try {
            when (operation) {
                "star" -> update(request, true)
                "unstar" -> update(request, false)
                "getStarred" -> getStarred(request, false)
                "getStarred2" -> getStarred(request, true)
                else -> error("Unknown starred operation")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in starred API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        if (result == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun update(request: StarredRequest, star: Boolean): Response {
        val username = request.username
        for (id in integerParameters(request.parameters, "id")) {
            if (mediaFiles.getMediaFile(id) == null) return notFound("Media file not found: $id")
            if (star) mediaFiles.starMediaFile(id, username) else mediaFiles.unstarMediaFile(id, username)
        }
        for (id in integerParameters(request.parameters, "albumId")) {
            if (albums.getAlbum(id) == null) return notFound("Album not found: $id")
            if (star) albums.starAlbum(id, username) else albums.unstarAlbum(id, username)
        }
        for (id in integerParameters(request.parameters, "artistId")) {
            if (artists.getArtist(id) == null) return notFound("Artist not found: $id")
            if (star) artists.starArtist(id, username) else artists.unstarArtist(id, username)
        }
        return writer.createResponse(true)
    }

    // Spring's getIntParameters ignores the entire group if any value is malformed.
    private fun integerParameters(parameters: Parameters, name: String): List<Int> {
        return parameters.getAll(name)?.map { it.toIntOrNull() ?: return emptyList() } ?: emptyList()
    }

    private fun getStarred(request: StarredRequest, second: Boolean): Response? {
        val username = request.username
        val player = requireNotNull(request.player)
        val folderId = request.parameters["musicFolderId"]?.let { it.toIntOrNull() ?: return null }
        val musicFolders = folders.getMusicFoldersForUser(username, folderId)
        val response = writer.createResponse(true)
        if (second) {
            val result = Starred2()
            artists.getStarredArtists(0, Int.MAX_VALUE, username, musicFolders).forEach {
                result.artist.add(mapper.artist(ArtistID3(), it, username))
            }
            albums.getStarredAlbums(0, Int.MAX_VALUE, username, musicFolders).forEach {
                result.album.add(mapper.album(AlbumID3(), it, username))
            }
            mediaFiles.getStarredFiles(0, Int.MAX_VALUE, username, musicFolders).forEach {
                result.song.add(mapper.child(player, it, username))
            }
            response.starred2 = result
        } else {
            val result = Starred()
            mediaFiles.getStarredDirectories(0, Int.MAX_VALUE, username, musicFolders).forEach {
                result.artist.add(mapper.artist(it, username))
            }
            mediaFiles.getStarredAlbums(0, Int.MAX_VALUE, username, musicFolders).forEach {
                result.album.add(mapper.child(player, it, username))
            }
            mediaFiles.getStarredFiles(0, Int.MAX_VALUE, username, musicFolders).forEach {
                result.song.add(mapper.child(player, it, username))
            }
            response.starred = result
        }
        return response
    }

    private fun notFound(message: String) = writer.createErrorResponse(ErrorCode.NOT_FOUND, message)

    companion object {
        private val LOG = LoggerFactory.getLogger(StarredController::class.java)
        @JvmField val PATHS = arrayOf(
            "/rest/star", "/rest/star.view", "/rest/unstar", "/rest/unstar.view",
            "/rest/getStarred", "/rest/getStarred.view", "/rest/getStarred2", "/rest/getStarred2.view",
        )
    }
}
