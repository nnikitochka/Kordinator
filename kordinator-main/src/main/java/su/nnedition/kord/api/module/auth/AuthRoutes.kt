package su.nnedition.kord.api.module.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.authDispatcher
import su.nnedition.kord.api.support.MAX_PASSWORD_LENGTH
import su.nnedition.kord.api.support.jsonMapper
import su.nnedition.kord.api.support.respondJson

fun Route.authRoutes(
    context: ApiContext
) {
    val authService = requireNotNull(context.authService)
    post("/auth/login") {
        val request = try {
            jsonMapper.readValue(call.receiveText(), LoginRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected username and password"), HttpStatusCode.BadRequest)
            return@post
        }
        if (request.username.length > 64 || request.password.length > MAX_PASSWORD_LENGTH) {
            call.respondJson(ApiError("invalid_request", "Username or password is too long"), HttpStatusCode.BadRequest)
            return@post
        }
        val source = call.request.local.remoteHost
        if (context.loginRateLimiter.isLimited(source)) {
            call.respondJson(ApiError("login_rate_limited", "Too many attempts; try again later"), HttpStatusCode.TooManyRequests)
            return@post
        }
        val session = withContext(authDispatcher) { authService.login(request.username, request.password) }
        if (session == null) {
            context.loginRateLimiter.recordFailure(source)
            call.respondJson(ApiError("invalid_credentials", "Invalid username or password"), HttpStatusCode.Unauthorized)
        } else {
            context.loginRateLimiter.clear(source)
            call.respondJson(LoginResponse(session.token, expiresAt = session.expiresAt.toString()))
        }
    }
    post("/auth/password-recovery") {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        val request = try {
            jsonMapper.readValue(call.receiveText(), PasswordRecoveryRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected an email address"), HttpStatusCode.BadRequest)
            return@post
        }
        val source = call.request.local.remoteHost
        if (!context.recoveryRateLimiter.isLimited(source)) {
            context.recoveryRateLimiter.recordFailure(source)
            withContext(Dispatchers.IO) { context.passwordRecoveryService?.request(request.email) }
        }
        call.respondJson(PasswordRecoveryAcceptedResponse(), HttpStatusCode.Accepted)
    }
    post("/auth/password-recovery/reset") {
        val service = context.passwordRecoveryService
        if (service == null) {
            call.respondJson(ApiError("unavailable", "Password recovery is not configured"), HttpStatusCode.ServiceUnavailable)
            return@post
        }
        val request = try {
            jsonMapper.readValue(call.receiveText(), CompletePasswordRecoveryRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected a recovery token and new password"), HttpStatusCode.BadRequest)
            return@post
        }
        val changed = try {
            withContext(authDispatcher) { service.reset(request.token, request.newPassword) }
        } catch (_: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_password", "Password must contain 12 to 1024 characters"), HttpStatusCode.BadRequest)
            return@post
        }
        if (changed) call.respond(HttpStatusCode.NoContent) else {
            call.respondJson(ApiError("invalid_recovery_token", "Recovery token is invalid or expired"), HttpStatusCode.BadRequest)
        }
    }
}
