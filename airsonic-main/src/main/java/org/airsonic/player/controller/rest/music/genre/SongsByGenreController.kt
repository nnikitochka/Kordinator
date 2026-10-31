package org.airsonic.player.controller.rest.music.genre

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
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.MediaFolderService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Songs
import kotlin.coroutines.cancellation.CancellationException

class SongsByGenreController(
    private val mediaFiles: MediaFileDao,
    private val folders: MediaFolderService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> SongsByGenreRequest) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: SongsByGenreRequest) {
        val params = request.parameters
        val genre = params["genre"]
        val rawFolderId = params["musicFolderId"]
        val folderId = if (rawFolderId == null) null else rawFolderId.toIntOrNull()
        if (genre != null && rawFolderId != null && folderId == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val result = try {
            if (genre == null) {
                writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Required param (genre) is missing")
            } else {
                val offset = params["offset"]?.toIntOrNull() ?: 0
                val count = (params["count"]?.toIntOrNull() ?: 10).coerceIn(0, 500)
                val songs = Songs()
                mediaFiles.getSongsByGenre(genre, offset, count, folders.getMusicFoldersForUser(request.username, folderId))
                    .forEach { songs.song.add(mapper.child(request.player, it, request.username)) }
                writer.createResponse(true).apply { songsByGenre = songs }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in songs by genre API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        val serialized = writer.serializeForType(params["f"] ?: "xml", params["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SongsByGenreController::class.java)
        @JvmField val PATHS = arrayOf("/rest/getSongsByGenre", "/rest/getSongsByGenre.view")
    }
}
