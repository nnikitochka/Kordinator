package org.airsonic.player.controller.rest.statistics

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.*
import org.subsonic.restapi.HealthComponentInfo
import org.subsonic.restapi.ServerHealth
import kotlin.coroutines.cancellation.CancellationException

/** Runs health contributors directly; does not delegate to an Actuator HTTP endpoint. */
class ServerHealthController(
    private val contributors: HealthContributorRegistry,
    private val aggregator: StatusAggregator,
    private val mapper: ObjectMapper,
    private val writer: JAXBWriter
) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        root.get(PATH) {
            val params = request(call).parameters
            call.response.headers.append("Cache-Control", "no-store")
            val response = try {
                val snapshot = withContext(Dispatchers.IO) {
                    ServerHealth().apply {
                        contributors.sortedBy { it.name }.forEach {
                            component.add(check(it.name, it.contributor, aggregator))
                        }
                        status = aggregator.getAggregateStatus(component.map { Status(it.status) }.toSet()).code
                    }
                }
                writer.createResponse(true).apply { serverHealth = snapshot }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                LOG.warn("Server health request failed", e)
                writer.createErrorResponse(ErrorCode.GENERIC, "Server health request failed")
            }
            val serialized = writer.serializeForType(params["f"] ?: "xml", params["callback"], response)
            call.respondText(serialized.value, ContentType.parse(serialized.key))
        }
    }

    private fun check(name: String, contributor: HealthContributor, aggregator: StatusAggregator): HealthComponentInfo =
        HealthComponentInfo().apply {
            this.name = name
            when (contributor) {
                is HealthIndicator -> {
                    val health = try { contributor.health() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        LOG.warn("Health contributor {} failed", name, e)
                        Health.down(e).build()
                    }
                    status = health.status.code
                    if (health.details.isNotEmpty()) details = mapper.writeValueAsString(health.details)
                }
                is CompositeHealthContributor -> {
                    contributor.sortedBy { it.name }.forEach { component.add(check(it.name, it.contributor, aggregator)) }
                    status = aggregator.getAggregateStatus(component.map { Status(it.status) }.toSet()).code
                }
            }
        }

    companion object {
        private val LOG = LoggerFactory.getLogger(ServerHealthController::class.java)
        const val PATH = "/rest/getServerHealth"
    }
}
