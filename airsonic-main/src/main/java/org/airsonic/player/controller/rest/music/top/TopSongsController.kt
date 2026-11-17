package org.airsonic.player.controller.rest.music.top

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.service.LastFmService
import org.airsonic.player.service.MediaFolderService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.TopSongs
import kotlin.coroutines.cancellation.CancellationException

class TopSongsController(
    private val lastFm: LastFmService,
    private val folders: MediaFolderService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> TopSongsRequest) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: TopSongsRequest) {
        val params = request.parameters
        val result = try {
            val artist = params["artist"]
            if (artist == null) {
                writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Required param (artist) is missing")
            } else {
                val count = params["count"]?.toIntOrNull() ?: 50
                val songs = lastFm.getTopSongs(artist, count, folders.getMusicFoldersForUser(request.username))
                val player = requireNotNull(request.player) { "Player unavailable" }
                writer.createResponse(true).apply {
                    topSongs = TopSongs().apply {
                        songs.forEach { song.add(mapper.child(player, it, request.username)) }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in top songs API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        val serialized = writer.serializeForType(params["f"] ?: "xml", params["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(TopSongsController::class.java)
        @JvmField val PATHS = arrayOf("/rest/getTopSongs", "/rest/getTopSongs.view")
    }
}
