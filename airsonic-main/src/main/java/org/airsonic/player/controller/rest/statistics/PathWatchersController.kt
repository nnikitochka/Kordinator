package org.airsonic.player.controller.rest.statistics

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.service.PathWatcherService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.PathWatcherInfo
import org.subsonic.restapi.PathWatchers
import kotlin.coroutines.cancellation.CancellationException

class PathWatchersController(private val watchers: PathWatcherService, private val writer: JAXBWriter) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        root.get(PATH) {
            val params = request(call).parameters
            call.response.headers.append("Cache-Control", "no-store")
            val result = try {
                writer.createResponse(true).apply {
                    pathWatchers = PathWatchers().apply {
                        watchers.watchers.toSortedMap().forEach { (id, directory) ->
                            watcher.add(PathWatcherInfo().apply { name = id; path = directory })
                        }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                LOG.warn("Path watchers request failed", e)
                writer.createErrorResponse(ErrorCode.GENERIC, "Path watchers request failed")
            }
            val serialized = writer.serializeForType(params["f"] ?: "xml", params["callback"], result)
            call.respondText(serialized.value, ContentType.parse(serialized.key))
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PathWatchersController::class.java)
        const val PATH = "/rest/getPathWatchers"
    }
}
