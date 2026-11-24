package su.nnedition.kord.api.module.share

import io.ktor.server.routing.Route

import io.ktor.server.auth.principal
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.request.receiveText
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import kotlinx.coroutines.withContext
import org.airsonic.player.ktor.catalogLibraryScope
import org.airsonic.player.ktor.jsonMapper
import org.airsonic.player.ktor.respondJson
import org.airsonic.player.ktor.toCatalogTrackResponse
import org.airsonic.player.ktor.toResponse
import org.airsonic.player.security.LocalUserPrincipal
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.authDispatcher
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.scanDispatcher
import java.time.Instant

fun Route.shareRoutes(
    context: ApiContext
) {
    get("/shares") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val service = context.shareService ?: return@get call.respondJson(
            ApiError(
                "unavailable",
                "Share storage is not configured"
            ), HttpStatusCode.ServiceUnavailable)
        val shares = withContext(authDispatcher) { service.list(actor.id) }
        call.respondJson(shares.map { it.toResponse(null, emptyList()) })
    }
    post("/shares") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val service = context.shareService ?: return@post call.respondJson(
            ApiError(
                "unavailable",
                "Share storage is not configured"
            ), HttpStatusCode.ServiceUnavailable)
        val catalog = context.mediaCatalogService ?: return@post call.respondJson(
            ApiError(
                "unavailable",
                "Track catalog is not configured"
            ), HttpStatusCode.ServiceUnavailable)
        val request = try {
            jsonMapper.readValue(call.receiveText(), CreateShareRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected trackIds, optional description and expiresAt"), HttpStatusCode.BadRequest)
            return@post
        }
        val expiresAt = try {
            request.expiresAt?.let(Instant::parse)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_expiration", "expiresAt must be an ISO-8601 timestamp"), HttpStatusCode.BadRequest)
            return@post
        }
        try {
            val share = withContext(scanDispatcher) {
                service.create(actor.id, request.trackIds, request.description, expiresAt, actor.catalogLibraryScope())
            }
            val tracks = withContext(scanDispatcher) { catalog.tracksByCoreIds(share.trackIds, actor.catalogLibraryScope()) }
            call.respondJson(share.toResponse(share.token, tracks.map { it.toCatalogTrackResponse() }), HttpStatusCode.Created)
        } catch (failure: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_share", failure.message ?: "Share request is invalid"), HttpStatusCode.BadRequest)
        }
    }
    put("/shares/{id}") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
        val service = context.shareService ?: return@put call.respondJson(
            ApiError(
                "unavailable",
                "Share storage is not configured"
            ), HttpStatusCode.ServiceUnavailable)
        val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it >= 0 }
            ?: return@put call.respondJson(ApiError("invalid_request", "Share ID must be non-negative"), HttpStatusCode.BadRequest)
        val request = try {
            jsonMapper.readValue(call.receiveText(), UpdateShareRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected description and expiresAt"), HttpStatusCode.BadRequest)
            return@put
        }
        val expiresAt = try {
            request.expiresAt?.let(Instant::parse)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_expiration", "expiresAt must be an ISO-8601 timestamp"), HttpStatusCode.BadRequest)
            return@put
        }
        try {
            val updated = withContext(authDispatcher) { service.update(actor.id, id, request.description, expiresAt) }
            if (!updated) call.respond(HttpStatusCode.NotFound)
            else call.respondJson(requireNotNull(withContext(authDispatcher) { service.get(actor.id, id) }).toResponse(null, emptyList()))
        } catch (failure: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_share", failure.message ?: "Share request is invalid"), HttpStatusCode.BadRequest)
        }
    }
    delete("/shares/{id}") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val service = context.shareService ?: return@delete call.respondJson(
            ApiError(
                "unavailable",
                "Share storage is not configured"
            ), HttpStatusCode.ServiceUnavailable
        )
        val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it >= 0 }
            ?: return@delete call.respondJson(ApiError("invalid_request", "Share ID must be non-negative"), HttpStatusCode.BadRequest)
        val deleted = withContext(authDispatcher) {
            service.delete(actor.id, id)
        }
        if (deleted) call.respond(HttpStatusCode.NoContent)
        else call.respond(HttpStatusCode.NotFound)
    }
}
