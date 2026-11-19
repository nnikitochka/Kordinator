package org.airsonic.player.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.request.receiveText
import org.airsonic.player.security.LoginRateLimiter

data class KordinatorLoginRequest(val username: String?, val password: String?)
data class KordinatorLoginResponse(val accessToken: String, val tokenType: String, val expiresAt: String)
data class KordinatorCurrentUserResponse(val username: String, val roles: Set<String>)

class KordinatorAuthRoutes(
    private val sessions: KordinatorSessionService,
    private val loginRateLimiter: LoginRateLimiter,
) {
    fun routes(root: Route, objectMapper: ObjectMapper) {
        root.post("/api/v1/auth/login") {
            val source = call.request.local.remoteHost
            if (loginRateLimiter.isLimited(source)) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("rate_limited", "Too many login attempts")),
                    ContentType.Application.Json,
                    HttpStatusCode.TooManyRequests,
                )
                return@post
            }

            val request = try {
                objectMapper.readValue(call.receiveText(), KordinatorLoginRequest::class.java)
            } catch (_: Exception) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Request body must be valid JSON")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@post
            }
            val username = request.username?.trim().orEmpty()
            val password = request.password.orEmpty()
            val token = sessions.login(username, password)
            if (token == null) {
                loginRateLimiter.recordFailure(source)
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("unauthorized", "Invalid username or password")),
                    ContentType.Application.Json,
                    HttpStatusCode.Unauthorized,
                )
                return@post
            }

            loginRateLimiter.clear(source)
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respondText(
                objectMapper.writeValueAsString(
                    KordinatorLoginResponse(token.accessToken, "Bearer", token.expiresAt.toString()),
                ),
                ContentType.Application.Json,
                HttpStatusCode.OK,
            )
        }

        root.get("/api/v1/me") {
            val principal = sessions.authenticate(call.bearerToken())
            if (principal == null) {
                unauthorized(call, objectMapper)
                return@get
            }
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respondText(
                objectMapper.writeValueAsString(KordinatorCurrentUserResponse(principal.username, principal.roles)),
                ContentType.Application.Json,
            )
        }

        root.post("/api/v1/auth/logout") {
            val token = call.bearerToken()
            if (sessions.authenticate(token) == null) {
                unauthorized(call, objectMapper)
                return@post
            }
            sessions.revoke(token)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun ApplicationCall.bearerToken(): String? {
    val authorization = request.headers[HttpHeaders.Authorization] ?: return null
    val separator = authorization.indexOf(' ')
    if (separator <= 0 || !authorization.substring(0, separator).equals("Bearer", ignoreCase = true)) return null
    return authorization.substring(separator + 1).trim().takeIf { it.isNotEmpty() }
}

private suspend fun unauthorized(call: ApplicationCall, objectMapper: ObjectMapper) {
    call.respondText(
        objectMapper.writeValueAsString(ApiError("unauthorized", "Valid bearer token required")),
        ContentType.Application.Json,
        HttpStatusCode.Unauthorized,
    )
}
