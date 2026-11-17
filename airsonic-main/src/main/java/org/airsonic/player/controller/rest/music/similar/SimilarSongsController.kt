package org.airsonic.player.controller.rest.music.similar

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.service.LastFmService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Response
import org.subsonic.restapi.SimilarSongs
import org.subsonic.restapi.SimilarSongs2
import kotlin.coroutines.cancellation.CancellationException

class SimilarSongsController(
    private val mediaFiles: MediaFileService,
    private val artists: ArtistDao,
    private val lastFm: LastFmService,
    private val folders: MediaFolderService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> SimilarSongsRequest) {
        with(root) {
            PATHS.forEach { path ->
                val second = path.removeSuffix(".view").endsWith("2")
                get(path) { respond(call, request(call), second) }
                post(path) { respond(call, request(call), second) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: SimilarSongsRequest, second: Boolean) {
        val response = try {
            similarSongs(request, second)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in similar songs API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: e.javaClass.simpleName)
        }
        val serialized = writer.serializeForType(
            request.parameters["f"] ?: "xml", request.parameters["callback"], response
        )
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun similarSongs(request: SimilarSongsRequest, second: Boolean): Response {
        val id = request.id ?: return writer.createErrorResponse(
            ErrorCode.MISSING_PARAMETER, "Required param (id) is missing"
        )
        val count = request.parameters["count"]?.toIntOrNull() ?: 50
        val songs = if (second) {
            val artist = artists.getArtist(id)
                ?: return writer.createErrorResponse(ErrorCode.NOT_FOUND, "Artist not found.")
            lastFm.getSimilarSongs(artist, count, folders.getMusicFoldersForUser(request.username))
        } else {
            val mediaFile = mediaFiles.getMediaFile(id)
                ?: return writer.createErrorResponse(ErrorCode.NOT_FOUND, "Media file not found.")
            lastFm.getSimilarSongs(mediaFile, count, folders.getMusicFoldersForUser(request.username))
        }
        val player = requireNotNull(request.player) { "Player unavailable" }
        return writer.createResponse(true).apply {
            if (second) {
                similarSongs2 = SimilarSongs2().apply {
                    songs.forEach { song.add(mapper.child(player, it, request.username)) }
                }
            } else {
                similarSongs = SimilarSongs().apply {
                    songs.forEach { song.add(mapper.child(player, it, request.username)) }
                }
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SimilarSongsController::class.java)
        @JvmField
        val PATHS = arrayOf(
            "/rest/getSimilarSongs", "/rest/getSimilarSongs.view",
            "/rest/getSimilarSongs2", "/rest/getSimilarSongs2.view",
        )
    }
}
