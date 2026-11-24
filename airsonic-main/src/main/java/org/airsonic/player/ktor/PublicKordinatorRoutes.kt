package org.airsonic.player.ktor

import io.ktor.server.routing.Route

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import kotlinx.coroutines.withContext
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.authDispatcher
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.module.share.PublicShareResponse
import su.nnedition.kord.api.module.share.PublicShareTrackResponse
import su.nnedition.kord.api.scanDispatcher


internal class PublicKordinatorRoutes(private val api: ApiContext) {
    fun routes(route: Route) = route.apply {
        with(api) {
        get("/api/v1/health") {
            call.respondJson(HealthResponse("UP"))
        }
        get("/api/v1/public/shares/{token}") {
            val service = shareService ?: return@get call.respondJson(
                ApiError(
                    "unavailable",
                    "Share storage is not configured"
                ), HttpStatusCode.ServiceUnavailable)
            val token = call.parameters["token"] ?: return@get call.respond(HttpStatusCode.NotFound)
            val share = withContext(authDispatcher) { service.publicShare(token) }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            val tracks = mediaCatalogService?.let { catalog ->
                withContext(scanDispatcher) { catalog.tracksByCoreIds(share.trackIds) }
            }.orEmpty()
            if (tracks.size != share.trackIds.size) return@get call.respond(HttpStatusCode.NotFound)
            call.respondJson(PublicShareResponse(share.description, share.expiresAt?.toString(), tracks.map {
                PublicShareTrackResponse(
                    it.toCatalogTrackResponse(),
                    "/api/v1/public/shares/$token/tracks/${it.id}/stream"
                )
            }))
        }
        get("/api/v1/public/shares/{token}/tracks/{trackId}/stream") {
            respondPublicShareTrack(call, shareService, libraryScanService, mediaCatalogService, head = false)
        }
        head("/api/v1/public/shares/{token}/tracks/{trackId}/stream") {
            respondPublicShareTrack(call, shareService, libraryScanService, mediaCatalogService, head = true)
        }
        }
    }
}
