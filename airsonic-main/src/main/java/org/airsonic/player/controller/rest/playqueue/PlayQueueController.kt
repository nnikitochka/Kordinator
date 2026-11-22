package org.airsonic.player.controller.rest.playqueue

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
import org.airsonic.player.dao.PlayQueueDao
import org.airsonic.player.domain.SavedPlayQueue
import org.airsonic.player.service.MediaFileService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Response
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class PlayQueueController(
    private val queues: PlayQueueDao,
    private val mediaFiles: MediaFileService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> PlayQueueRequest) {
        with(root) {
            PATHS.forEach { path ->
                val operation = path.substringAfterLast('/').removeSuffix(".view")
                get(path) { respond(call, request(call), operation) }
                post(path) { respond(call, request(call), operation) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: PlayQueueRequest, operation: String) {
        val result = try {
            when (operation) {
                "getPlayQueue" -> getPlayQueue(request)
                "savePlayQueue" -> savePlayQueue(request)
                else -> error("Unknown play queue operation")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in play queue API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        if (result == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun getPlayQueue(request: PlayQueueRequest): Response {
        val player = requireNotNull(request.player)
        val queue = queues.getPlayQueue(request.username) ?: return writer.createResponse(true)
        val result = org.subsonic.restapi.PlayQueue().apply {
            username = queue.username
            current = queue.currentMediaFileId
            position = queue.positionMillis
            changed = writer.convertDate(queue.changed)
            changedBy = queue.changedBy
            queue.mediaFileIds!!.forEach { id ->
                val mediaFile = mediaFiles.getMediaFile(id)
                if (mediaFile != null) entry.add(mapper.child(player, mediaFile, request.username))
            }
        }
        return writer.createResponse(true).apply { playQueue = result }
    }

    private fun savePlayQueue(request: PlayQueueRequest): Response? {
        val params = request.parameters
        val ids = params.getAll("id")?.map { it.toIntOrNull() ?: return@map null }
            ?.let { values -> if (values.any { it == null }) emptyList() else values.filterNotNull() } ?: emptyList()
        val current = params["current"]?.let { it.toIntOrNull() ?: return null }
        val position = params["position"]?.let { it.toLongOrNull() ?: return null }
        val client = params["c"] ?: return writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Required param (c) is missing")
        if (!ids.contains(current)) {
            return writer.createErrorResponse(ErrorCode.GENERIC, "Current track is not included in play queue")
        }
        queues.savePlayQueue(SavedPlayQueue(null, request.username, ids, current, position, Instant.now(), client))
        return writer.createResponse(true)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PlayQueueController::class.java)
        @JvmField val PATHS = arrayOf(
            "/rest/getPlayQueue", "/rest/getPlayQueue.view",
            "/rest/savePlayQueue", "/rest/savePlayQueue.view",
        )
    }
}
