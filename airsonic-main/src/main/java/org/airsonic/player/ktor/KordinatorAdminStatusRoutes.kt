package org.airsonic.player.ktor

import io.ktor.server.routing.Route

import io.ktor.server.auth.principal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.health.SimpleStatusAggregator
import org.airsonic.player.health.Status
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError


internal class KordinatorAdminStatusRoutes(private val api: ApiContext) {
    fun routes(route: Route) = route.apply {
        with(api) {
        get("/admin/health") {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@get
            }
            val contributors = healthContributors
                ?: return@get call.respondJson(ApiError("unavailable", "Health contributors are not configured"), HttpStatusCode.ServiceUnavailable)
            val aggregator = healthStatusAggregator ?: SimpleStatusAggregator()
            val components = withContext(Dispatchers.IO) {
                contributors.toList().sortedBy { it.name }.map { it.toHealthComponent(aggregator) }
            }
            call.respondJson(AdminHealthResponse(aggregator.getAggregateStatus(components.map { Status(it.status) }.toSet()).code, components))
        }
        get("/admin/path-watchers") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@get
            }
            val watchers = pathWatcherService
                ?: return@get call.respondJson(ApiError("unavailable", "Path watcher service is not configured"), HttpStatusCode.ServiceUnavailable)
            call.respondJson(watchers.watchers.toSortedMap().map { (name, path) -> PathWatcherResponse(name, path) })
        }
        get("/admin/scheduled-tasks") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@get
            }
            val tasks = taskSchedulingService
                ?: return@get call.respondJson(ApiError("unavailable", "Task scheduler is not configured"), HttpStatusCode.ServiceUnavailable)
            call.respondJson(tasks.taskInfos.sortedBy { it.name }.map { task ->
                val metadata = task.runMetadata
                ScheduledTaskResponse(
                    task.name, task.created.toString(), task.scheduledBy,
                    metadata?.type?.name, metadata?.firstRun?.toString(), metadata?.lastRun?.toString(), metadata?.nextRun?.toString(),
                )
            })
        }
        get("/admin/cache-statistics") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@get
            }
            val caches = cacheManager
                ?: return@get call.respondJson(ApiError("unavailable", "Cache manager is not configured"), HttpStatusCode.ServiceUnavailable)
            val meters = meterRegistry
                ?: return@get call.respondJson(ApiError("unavailable", "Meter registry is not configured"), HttpStatusCode.ServiceUnavailable)
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respondJson(caches.cacheNames.sorted().map { name ->
                CacheStatisticsResponse(
                    name,
                    cacheMetric(meters, "cache.gets", name, "result", "hit"),
                    cacheMetric(meters, "cache.gets", name, "result", "miss"),
                    cacheMetric(meters, "cache.puts", name),
                    cacheMetric(meters, "cache.removals", name),
                )
            })
        }
        delete("/admin/cache-statistics/{name}") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@delete
            }
            val caches = cacheManager
                ?: return@delete call.respondJson(ApiError("unavailable", "Cache manager is not configured"), HttpStatusCode.ServiceUnavailable)
            val name = call.parameters["name"]
                ?: return@delete call.respondJson(ApiError("missing_parameter", "Missing cache name"), HttpStatusCode.BadRequest)
            val cache = caches.getCache<Any, Any>(name)
                ?: return@delete call.respondJson(ApiError("not_found", "Cache not found"), HttpStatusCode.NotFound)
            withContext(Dispatchers.IO) { cache.removeAll() }
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respond(HttpStatusCode.NoContent)
        }
        get("/admin/transfer-statistics") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@get
            }
            val status = statusService
                ?: return@get call.respondJson(ApiError("unavailable", "Transfer status service is not configured"), HttpStatusCode.ServiceUnavailable)
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            val transfers = withContext(Dispatchers.IO) {
                buildList {
                    status.getAllStreamStatuses().filter { it.millisSinceLastUpdate < 3_600_000L }
                        .forEach { add(it.toTransferResponse("stream")) }
                    status.getAllDownloadStatuses().forEach { add(it.toTransferResponse("download")) }
                    status.getAllUploadStatuses().forEach { add(it.toTransferResponse("upload")) }
                }
            }
            call.respondJson(transfers)
        }
        get("/admin/user-transfer-statistics") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@get
            }
            val security = securityService
                ?: return@get call.respondJson(ApiError("unavailable", "User statistics service is not configured"), HttpStatusCode.ServiceUnavailable)
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            val users = withContext(Dispatchers.IO) {
                security.allUsers.sortedBy { it.username }.map { user ->
                    UserTransferStatisticsResponse(user.username, user.bytesStreamed, user.bytesDownloaded, user.bytesUploaded)
                }
            }
            call.respondJson(users)
        }
        get("/now-playing") {
            call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val status = statusService
                ?: return@get call.respondJson(ApiError("unavailable", "Playback status service is not configured"), HttpStatusCode.ServiceUnavailable)
            val playing = withContext(Dispatchers.IO) {
                (status.getActivePlays() + status.getInactivePlays()).mapNotNull { it.toNowPlayingResponse() }
            }
            call.respondJson(playing)
        }
        get("/admin/server-info") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@get
            }
            val info = serverInfoService
                ?: return@get call.respondJson(ApiError("unavailable", "Server information service is not configured"), HttpStatusCode.ServiceUnavailable)
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respondJson(withContext(Dispatchers.IO) { info.snapshot("Kordinator") })
        }
        get("/admin/server-log") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@get
            }
            val info = serverInfoService
                ?: return@get call.respondJson(ApiError("unavailable", "Server information service is not configured"), HttpStatusCode.ServiceUnavailable)
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respondJson(withContext(Dispatchers.IO) { info.log() })
        }
        get("/admin/getting-started") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@get
            }
            val settings = settingsService
                ?: return@get call.respondJson(ApiError("unavailable", "Settings service is not configured"), HttpStatusCode.ServiceUnavailable)
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respondJson(mapOf(
                "runningAsRoot" to (System.getProperty("user.name") == "root"),
                "enabled" to settings.isGettingStartedEnabled,
            ))
        }
        delete("/admin/getting-started") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@delete
            }
            val settings = settingsService
                ?: return@delete call.respondJson(ApiError("unavailable", "Settings service is not configured"), HttpStatusCode.ServiceUnavailable)
            withContext(Dispatchers.IO) {
                settings.isGettingStartedEnabled = false
                settings.save()
            }
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respond(HttpStatusCode.NoContent)
        }
        }
    }
}
