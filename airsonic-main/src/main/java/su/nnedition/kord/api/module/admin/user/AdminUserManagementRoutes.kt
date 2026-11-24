package su.nnedition.kord.api.module.admin.user

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.put
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LastEnabledAdministratorException
import org.airsonic.player.security.LocalUserPrincipal
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.authDispatcher
import su.nnedition.kord.api.module.admin.user.RenameUserRequest
import su.nnedition.kord.api.module.admin.user.ResetUserPasswordRequest
import su.nnedition.kord.api.module.admin.user.SetUserEmailRequest
import su.nnedition.kord.api.module.admin.user.SetUserEnabledRequest
import su.nnedition.kord.api.module.admin.user.SetUserRoleRequest
import su.nnedition.kord.api.support.jsonMapper
import su.nnedition.kord.api.support.respondJson

fun Route.adminUserManagementRoutes(
    context: ApiContext,
) {
    val authService = requireNotNull(context.authService)
    put("/admin/users/{id}") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@put
        }
        val targetId = call.parameters["id"]?.toLongOrNull()
        if (targetId == null || targetId < 0) {
            call.respondJson(ApiError("invalid_request", "User ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return@put
        }
        val request = try {
            jsonMapper.readValue(call.receiveText(), RenameUserRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected username"), HttpStatusCode.BadRequest)
            return@put
        }
        try {
            val renamed = withContext(authDispatcher) { authService.renameUser(targetId, request.username) }
            if (renamed) call.respond(HttpStatusCode.NoContent) else {
                call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
            }
        } catch (_: IllegalArgumentException) {
            call.respondJson(
                ApiError(
                    "invalid_username",
                    "Username must contain 3 to 64 letters, digits, dots, underscores or hyphens"
                ), HttpStatusCode.BadRequest)
        } catch (failure: java.sql.SQLException) {
            if (failure.sqlState?.startsWith("23") == true) {
                call.respondJson(
                    ApiError(
                        "username_exists",
                        "An account with that username already exists"
                    ), HttpStatusCode.Conflict)
            } else {
                throw failure
            }
        }
    }
    delete("/admin/users/{id}") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@delete
        }
        val targetId = call.parameters["id"]?.toLongOrNull()
        if (targetId == null || targetId < 0) {
            call.respondJson(ApiError("invalid_request", "User ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return@delete
        }
        if (targetId == actor.id) {
            call.respondJson(
                ApiError(
                    "invalid_operation",
                    "Cannot delete the current administrator account"
                ), HttpStatusCode.Conflict)
            return@delete
        }
        try {
            val deleted = withContext(authDispatcher) { authService.deleteUser(targetId) }
            if (deleted) call.respond(HttpStatusCode.NoContent) else {
                call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
            }
        } catch (_: LastEnabledAdministratorException) {
            call.respondJson(
                ApiError(
                    "last_administrator",
                    "At least one enabled administrator must remain"
                ), HttpStatusCode.Conflict)
        }
    }
    put("/admin/users/{id}/role") {
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
        if (targetId == actor.id) {
            call.respondJson(
                ApiError(
                    "invalid_operation",
                    "Cannot change the current administrator role"
                ), HttpStatusCode.Conflict)
            return@put
        }
        val request = try {
            jsonMapper.readValue(call.receiveText(), SetUserRoleRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected role"), HttpStatusCode.BadRequest)
            return@put
        }
        try {
            val updated = withContext(authDispatcher) { authService.setUserRole(targetId, request.role) }
            if (updated) call.respond(HttpStatusCode.NoContent) else {
                call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
            }
        } catch (_: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_role", "Role must be USER or ADMIN"), HttpStatusCode.BadRequest)
        } catch (_: LastEnabledAdministratorException) {
            call.respondJson(
                ApiError(
                    "last_administrator",
                    "At least one enabled administrator must remain"
                ), HttpStatusCode.Conflict)
        }
    }
    put("/admin/users/{id}/enabled") {
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
        if (targetId == actor.id) {
            call.respondJson(
                ApiError(
                    "invalid_operation",
                    "Cannot disable the current administrator account"
                ), HttpStatusCode.Conflict)
            return@put
        }
        val request = try {
            jsonMapper.readValue(call.receiveText(), SetUserEnabledRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected enabled"), HttpStatusCode.BadRequest)
            return@put
        }
        val changed = withContext(authDispatcher) { authService.setUserEnabled(targetId, request.enabled) }
        if (changed) call.respond(HttpStatusCode.NoContent) else {
            call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
        }
    }
    put("/admin/users/{id}/password") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@put
        }
        val targetId = call.parameters["id"]?.toLongOrNull()
        if (targetId == null || targetId < 0) {
            call.respondJson(ApiError("invalid_request", "User ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return@put
        }
        val request = try {
            jsonMapper.readValue(call.receiveText(), ResetUserPasswordRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected password"), HttpStatusCode.BadRequest)
            return@put
        }
        try {
            val reset = withContext(authDispatcher) { authService.resetPassword(targetId, request.password) }
            if (reset) call.respond(HttpStatusCode.NoContent) else {
                call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
            }
        } catch (_: IllegalArgumentException) {
            call.respondJson(
                ApiError(
                    "invalid_password",
                    "Password must contain 12 to 1024 characters"
                ), HttpStatusCode.BadRequest)
        }
    }
    put("/admin/users/{id}/email") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@put
        }
        val targetId = call.parameters["id"]?.toLongOrNull()
        if (targetId == null || targetId < 0) {
            call.respondJson(ApiError("invalid_request", "User ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return@put
        }
        val request = try {
            jsonMapper.readValue(call.receiveText(), SetUserEmailRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected email or null"), HttpStatusCode.BadRequest)
            return@put
        }
        try {
            val updated = withContext(authDispatcher) { authService.setUserEmail(targetId, request.email) }
            if (updated) call.respond(HttpStatusCode.NoContent) else {
                call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
            }
        } catch (_: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_email", "Email address is invalid"), HttpStatusCode.BadRequest)
        } catch (failure: java.sql.SQLException) {
            if (failure.sqlState?.startsWith("23") == true) {
                call.respondJson(ApiError("email_exists", "An account already uses that email address"), HttpStatusCode.Conflict)
            } else {
                throw failure
            }
        }
    }
}
