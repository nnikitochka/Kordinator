package org.airsonic.player.controller.rest.videos

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
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
import org.subsonic.restapi.Videos
import kotlin.coroutines.cancellation.CancellationException

class VideosController(
    private val mediaFileDao: MediaFileDao,
    private val folders: MediaFolderService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> VideosRequest) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: VideosRequest) {
        val params = request.parameters
        val result = try {
            // Spring's optional-with-default parameters silently fall back on malformed values.
            val size = params["size"]?.toIntOrNull() ?: Int.MAX_VALUE
            val offset = params["offset"]?.toIntOrNull() ?: 0
            val musicFolders = folders.getMusicFoldersForUser(request.username)
            val videos = Videos().apply {
                mediaFileDao.getVideos(size, offset, musicFolders).forEach {
                    video.add(mapper.child(request.player, it, request.username))
                }
            }
            writer.createResponse(true).apply { this.videos = videos }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in videos API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        val serialized = writer.serializeForType(params["f"] ?: "xml", params["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(VideosController::class.java)
        @JvmField val PATHS = arrayOf("/rest/getVideos", "/rest/getVideos.view")
    }
}
