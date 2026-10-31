package org.airsonic.player.controller.rest.albums

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
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
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.RatingService
import org.airsonic.player.service.SearchService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.AlbumID3
import org.subsonic.restapi.AlbumList
import org.subsonic.restapi.AlbumList2
import org.subsonic.restapi.AlbumWithSongsID3
import org.subsonic.restapi.Response
import kotlin.coroutines.cancellation.CancellationException

class AlbumsController(
    private val albums: AlbumDao,
    private val mediaFileDao: MediaFileDao,
    private val mediaFiles: MediaFileService,
    private val folders: MediaFolderService,
    private val ratings: RatingService,
    private val search: SearchService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> AlbumsRequest) {
        with(root) {
            PATHS.forEach { path ->
                val operation = path.substringAfterLast('/').removeSuffix(".view")
                get(path) { respond(call, request(call), operation) }
                post(path) { respond(call, request(call), operation) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: AlbumsRequest, operation: String) {
        val result = try {
            when (operation) {
                "getAlbum" -> getAlbum(request)
                "getAlbumList" -> getAlbumList(request, false)
                "getAlbumList2" -> getAlbumList(request, true)
                else -> error("Unknown album operation")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in albums API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        if (result == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun getAlbum(request: AlbumsRequest): Response {
        val id = request.id ?: return missing("id")
        val album = albums.getAlbum(id) ?: return errorResponse(ErrorCode.NOT_FOUND, "Album not found.")
        val result = mapper.album(AlbumWithSongsID3(), album, request.username)
        mediaFileDao.getSongsForAlbum(album.artist, album.name).forEach {
            result.song.add(mapper.child(requireNotNull(request.player), it, request.username))
        }
        return writer.createResponse(true).apply { this.album = result }
    }

    private fun getAlbumList(request: AlbumsRequest, second: Boolean): Response? {
        val params = request.parameters
        val size = (params["size"]?.toIntOrNull() ?: 10).coerceIn(0, 500)
        val offset = params["offset"]?.toIntOrNull() ?: 0
        val type = params["type"]
        if (second && type == null) return missing("type")
        val folderId = params["musicFolderId"]?.let { it.toIntOrNull() ?: return null }
        val musicFolders = folders.getMusicFoldersForUser(request.username, folderId)
        if (type == null) return missing("type")
        val genre = if (type == "byGenre") params["genre"] ?: return missing("genre") else null
        val fromYear: Int
        val toYear: Int
        if (type == "byYear") {
            val rawFrom = params["fromYear"] ?: return missing("fromYear")
            fromYear = rawFrom.toIntOrNull() ?: return null
            val rawTo = params["toYear"] ?: return missing("toYear")
            toYear = rawTo.toIntOrNull() ?: return null
        } else {
            fromYear = 0
            toYear = 0
        }
        if (second) {
            val found = when (type) {
                "frequent" -> albums.getMostFrequentlyPlayedAlbums(offset, size, musicFolders)
                "recent" -> albums.getMostRecentlyPlayedAlbums(offset, size, musicFolders)
                "newest" -> albums.getNewestAlbums(offset, size, musicFolders)
                "alphabeticalByArtist" -> albums.getAlphabeticalAlbums(offset, size, true, false, musicFolders)
                "alphabeticalByName" -> albums.getAlphabeticalAlbums(offset, size, false, false, musicFolders)
                "byGenre" -> albums.getAlbumsByGenre(offset, size, genre, musicFolders)
                "byYear" -> albums.getAlbumsByYear(offset, size, fromYear, toYear, musicFolders)
                "starred" -> albums.getStarredAlbums(offset, size, request.username, musicFolders)
                "random" -> search.getRandomAlbumsId3(size, musicFolders)
                else -> return invalidType(type)
            }
            val result = AlbumList2()
            found.forEach { result.album.add(mapper.album(AlbumID3(), it, request.username)) }
            return writer.createResponse(true).apply { albumList2 = result }
        }
        val found = when (type) {
            "highest" -> ratings.getHighestRatedAlbums(offset, size, musicFolders)
            "frequent" -> mediaFiles.getMostFrequentlyPlayedAlbums(offset, size, musicFolders)
            "recent" -> mediaFiles.getMostRecentlyPlayedAlbums(offset, size, musicFolders)
            "newest" -> mediaFiles.getNewestAlbums(offset, size, musicFolders)
            "starred" -> mediaFiles.getStarredAlbums(offset, size, request.username, musicFolders)
            "alphabeticalByArtist" -> mediaFiles.getAlphabeticalAlbums(offset, size, true, musicFolders)
            "alphabeticalByName" -> mediaFiles.getAlphabeticalAlbums(offset, size, false, musicFolders)
            "byGenre" -> mediaFiles.getAlbumsByGenre(offset, size, genre, musicFolders)
            "byYear" -> mediaFiles.getAlbumsByYear(offset, size, fromYear, toYear, musicFolders)
            "random" -> search.getRandomAlbums(size, musicFolders)
            else -> return invalidType(type)
        }
        val result = AlbumList()
        found.forEach { result.album.add(mapper.child(requireNotNull(request.player), it, request.username)) }
        return writer.createResponse(true).apply { albumList = result }
    }

    private fun missing(name: String) = errorResponse(ErrorCode.MISSING_PARAMETER, "Required param ($name) is missing")
    private fun invalidType(type: String) = errorResponse(ErrorCode.GENERIC, "Request processing failed: java.lang.Exception: Invalid list type: $type")
    private fun errorResponse(code: ErrorCode, message: String) = writer.createErrorResponse(code, message)

    companion object {
        private val LOG = LoggerFactory.getLogger(AlbumsController::class.java)
        @JvmField val PATHS = arrayOf(
            "/rest/getAlbum", "/rest/getAlbum.view",
            "/rest/getAlbumList", "/rest/getAlbumList.view",
            "/rest/getAlbumList2", "/rest/getAlbumList2.view",
        )
    }
}
