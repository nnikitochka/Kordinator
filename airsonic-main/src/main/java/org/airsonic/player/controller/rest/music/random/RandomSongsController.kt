package org.airsonic.player.controller.rest.music.random

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
import org.airsonic.player.domain.RandomSearchCriteria
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SearchService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Songs
import kotlin.coroutines.cancellation.CancellationException

class RandomSongsController(
    private val search: SearchService,
    private val folders: MediaFolderService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> RandomSongsRequest) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: RandomSongsRequest) {
        val params = request.parameters
        val fromYear = params["fromYear"]?.toIntOrNull()
        val toYear = params["toYear"]?.toIntOrNull()
        val folderId = params["musicFolderId"]?.toIntOrNull()
        if ((params["fromYear"] != null && fromYear == null) ||
            (params["toYear"] != null && toYear == null) ||
            (params["musicFolderId"] != null && folderId == null)) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val result = try {
            val size = (params["size"]?.toIntOrNull() ?: 10).coerceIn(0, 500)
            val criteria = RandomSearchCriteria(size, params["genre"], fromYear, toYear,
                folders.getMusicFoldersForUser(request.username, folderId))
            val songs = Songs().apply {
                search.getRandomSongs(criteria).forEach { song.add(mapper.child(request.player, it, request.username)) }
            }
            writer.createResponse(true).apply { randomSongs = songs }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in random songs API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        val serialized = writer.serializeForType(params["f"] ?: "xml", params["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RandomSongsController::class.java)
        @JvmField val PATHS = arrayOf("/rest/getRandomSongs", "/rest/getRandomSongs.view")
    }
}
