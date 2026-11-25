package su.nnedition.kord.api.module.admin.user

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.withContext
import su.nnedition.kord.security.LocalUserPrincipal
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.authDispatcher
import su.nnedition.kord.api.support.jsonMapper
import su.nnedition.kord.api.support.respondJson

fun Route.adminUserRoutes(
    context: ApiContext,
) {
    val authService = requireNotNull(context.authService)
    get("/admin/users") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@get
        }
        val users = withContext(authDispatcher) {
            authService.users().map { AdminUserResponse(it.id, it.username, it.role, it.enabled, it.email) }
        }
        call.respondJson(users)
    }
    get("/admin/users/{id}") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@get
        }
        val targetId = call.parameters["id"]?.toLongOrNull()
        if (targetId == null || targetId < 0) {
            call.respondJson(ApiError("invalid_request", "User ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return@get
        }
        val user = withContext(authDispatcher) { authService.users().firstOrNull { it.id == targetId } }
        val libraries = withContext(authDispatcher) { authService.allowedLibraries(targetId) }
        if (user == null || libraries == null) call.respond(HttpStatusCode.NotFound)
        else call.respondJson(AdminUserDetailsResponse(
                AdminUserResponse(user.id, user.username, user.role, user.enabled, user.email),
                libraries.sorted(),
        ))
    }
    post("/admin/users") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@post call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@post
        }
        val request = try {
            jsonMapper.readValue(call.receiveText(), CreateUserRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(
                ApiError(
                    "invalid_request",
                    "Expected username, password and optional role"
                ), HttpStatusCode.BadRequest)
            return@post
        }
        try {
            val id = withContext(authDispatcher) {
                authService.createUser(request.username, request.password, request.role, request.email)
            }
            call.respondJson(
                AdminUserResponse(id, request.username, request.role, true, request.email?.trim()?.lowercase()),
                HttpStatusCode.Created,
            )
        } catch (_: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_account", "Username, password or role is invalid"), HttpStatusCode.BadRequest)
        } catch (failure: java.sql.SQLException) {
            if (failure.sqlState?.startsWith("23") == true) {
                call.respondJson(
                    ApiError(
                        "account_exists",
                        "That username or email address is already in use"
                    ), HttpStatusCode.Conflict)
            } else {
                throw failure
            }
        }
    }
}
