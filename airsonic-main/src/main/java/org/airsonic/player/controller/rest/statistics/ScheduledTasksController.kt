package org.airsonic.player.controller.rest.statistics

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.service.TaskSchedulingService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.ScheduledTaskInfo
import org.subsonic.restapi.ScheduledTasks
import kotlin.coroutines.cancellation.CancellationException

class ScheduledTasksController(private val tasks: TaskSchedulingService, private val writer: JAXBWriter) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        root.get(PATH) {
            val params = request(call).parameters
            call.response.headers.append("Cache-Control", "no-store")
            val result = try {
                writer.createResponse(true).apply {
                    scheduledTasks = ScheduledTasks().apply {
                        tasks.taskInfos.sortedBy { it.name() }.forEach { value -> task.add(ScheduledTaskInfo().apply {
                            name = value.name(); created = value.created()?.toString(); scheduledBy = value.scheduledBy()
                            value.runMetadata()?.let { run ->
                                type = run.type.name; firstRun = run.firstRun?.toString()
                                lastRun = run.lastRun?.toString(); nextRun = run.nextRun?.toString()
                            }
                        }) }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                LOG.warn("Scheduled tasks request failed", e)
                writer.createErrorResponse(ErrorCode.GENERIC, "Scheduled tasks request failed")
            }
            val serialized = writer.serializeForType(params["f"] ?: "xml", params["callback"], result)
            call.respondText(serialized.value, ContentType.parse(serialized.key))
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ScheduledTasksController::class.java)
        const val PATH = "/rest/getScheduledTasks"
    }
}
