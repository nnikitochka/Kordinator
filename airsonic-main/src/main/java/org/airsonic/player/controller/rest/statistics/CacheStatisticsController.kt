package org.airsonic.player.controller.rest.statistics

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.slf4j.LoggerFactory
import org.subsonic.restapi.CacheStatistics
import org.subsonic.restapi.CacheStatisticsEntry
import javax.cache.CacheManager
import kotlin.coroutines.cancellation.CancellationException

class CacheStatisticsController(private val caches: CacheManager, private val meters: MeterRegistry,
                                private val writer: JAXBWriter) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        with(root) {
            get(READ_PATH) { respond(call, request(call), false) }
            post(CLEAR_PATH) { respond(call, request(call), true) }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: UsersRequest, clear: Boolean) {
        val result = try {
            withContext(Dispatchers.IO) {
                if (clear) {
                    val name = request.parameters["cache"]
                    val cache = name?.let { caches.getCache<Any, Any>(it) }
                    when {
                        name.isNullOrBlank() -> writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Missing cache")
                        cache == null -> writer.createErrorResponse(ErrorCode.NOT_FOUND, "Cache not found")
                        // Match Spring's previous JCache eviction, including removal events and counters.
                        else -> { cache.removeAll(); writer.createResponse(true) }
                    }
                } else writer.createResponse(true).apply { cacheStatistics = snapshot() }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            LOG.warn("Cache diagnostics request failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Cache diagnostics request failed")
        }
        call.response.headers.append("Cache-Control", "no-store")
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun snapshot() = CacheStatistics().apply {
        caches.cacheNames.sorted().forEach { cacheName ->
            cache.add(CacheStatisticsEntry().apply {
                name = cacheName
                hits = metric("cache.gets", cacheName, "result", "hit")
                misses = metric("cache.gets", cacheName, "result", "miss")
                puts = metric("cache.puts", cacheName)
                removals = metric("cache.removals", cacheName)
            })
        }
    }

    private fun metric(name: String, cache: String, vararg tags: String): Double? {
        val found = meters.find(name).tags("cache", cache).tags(*tags).meters()
        if (found.isEmpty()) return null
        val value = found.sumOf { meter -> meter.measure().sumOf { it.value } }
        return value.takeIf { it.isFinite() }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(CacheStatisticsController::class.java)
        const val READ_PATH = "/rest/getCacheStatistics"
        const val CLEAR_PATH = "/rest/clearCache"
        @JvmField val PATHS = arrayOf(READ_PATH, CLEAR_PATH)
    }
}
