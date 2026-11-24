package su.nnedition.kord.api.module.admin.user

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LocalUserPrincipal
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.authDispatcher
import su.nnedition.kord.api.module.admin.user.SetUserLibrariesRequest
import su.nnedition.kord.api.support.jsonMapper
import su.nnedition.kord.api.support.respondJson

fun Route.adminUserLibrariesRoutes(
    context: ApiContext,
) {
    val authService = requireNotNull(context.authService)
    get("/admin/users/{id}/libraries") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@get
        }
        val targetId = call.parameters["id"]?.toLongOrNull()
        if (targetId == null) {
            call.respondJson(ApiError("invalid_request", "User ID must be a number"), HttpStatusCode.BadRequest)
            return@get
        }
        val libraries = withContext(authDispatcher) { authService.allowedLibraries(targetId) }
        if (libraries == null) call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
        else call.respondJson(libraries.sorted())
    }
    put("/admin/users/{id}/libraries") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@put
        }
        val targetId = call.parameters["id"]?.toLongOrNull()
        if (targetId == null) {
            call.respondJson(ApiError("invalid_request", "User ID must be a number"), HttpStatusCode.BadRequest)
            return@put
        }
        val request = try {
            jsonMapper.readValue(call.receiveText(), SetUserLibrariesRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected a libraryIds array"), HttpStatusCode.BadRequest)
            return@put
        }
        val validLibraryIds = context.mediaFolderService?.let { service ->
            withContext(authDispatcher) { service.getAllMusicFolders(true, true, true) }
            .mapNotNull { it.id?.toLong() }.toSet()
        }.orEmpty()
        if (!validLibraryIds.containsAll(request.libraryIds)) {
            call.respondJson(ApiError("invalid_library", "One or more libraries do not exist"), HttpStatusCode.BadRequest)
            return@put
        }
        val updated = withContext(authDispatcher) {
            authService.setAllowedLibraries(targetId, request.libraryIds)
        }
        if (updated) call.respond(HttpStatusCode.NoContent) else {
            call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
        }
    }
}
