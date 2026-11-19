package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.request.receiveText
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.security.LoginRateLimiter
import org.airsonic.player.runtime.LifecycleRegistry
import org.airsonic.player.runtime.RuntimeConfigurationLoader
import org.airsonic.player.runtime.StandaloneDatabase

private val authDispatcher = Dispatchers.IO.limitedParallelism(4)

fun Application.module() {
    routing {
        get("/internal/ktor/ping") {
            call.respondText("pong")
        }
    }
}

/** Minimal new API module used while the application is moving to a standalone Ktor process. */
fun Application.standaloneModule(authService: LocalAuthService? = null) {
    val loginRateLimiter = LoginRateLimiter()
    install(Authentication) {
        bearer("server-session") {
            authenticate { credentials ->
                authService?.let { withContext(authDispatcher) { it.authenticate(credentials.token) } }
            }
        }
    }
    routing {
        get("/api/v1/health") {
            call.respondJson(HealthResponse("UP"))
        }
        if (authService != null) {
            route("/api/v1") {
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
                    if (loginRateLimiter.isLimited(source)) {
                        call.respondJson(ApiError("login_rate_limited", "Too many attempts; try again later"), HttpStatusCode.TooManyRequests)
                        return@post
                    }
                    val session = withContext(authDispatcher) { authService.login(request.username, request.password) }
                    if (session == null) {
                        loginRateLimiter.recordFailure(source)
                        call.respondJson(ApiError("invalid_credentials", "Invalid username or password"), HttpStatusCode.Unauthorized)
                    } else {
                        loginRateLimiter.clear(source)
                        call.respondJson(LoginResponse(session.token, expiresAt = session.expiresAt.toString()))
                    }
                }
                authenticate("server-session") {
                    get("/me") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        call.respondJson(CurrentUserResponse(user.id, user.username, user.role))
                    }
                    put("/me/password") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@put call.respond(HttpStatusCode.Unauthorized)
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), ChangePasswordRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected currentPassword and newPassword"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        if (request.currentPassword.length > MAX_PASSWORD_LENGTH || request.newPassword.length > MAX_PASSWORD_LENGTH) {
                            call.respondJson(ApiError("invalid_request", "Password is too long"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val changed = try {
                            withContext(authDispatcher) {
                                authService.changePassword(user.id, request.currentPassword, request.newPassword)
                            }
                        } catch (_: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_password", "New password does not meet the requirements"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        if (changed) call.respond(HttpStatusCode.NoContent) else {
                            call.respondJson(ApiError("invalid_password", "Current password is invalid"), HttpStatusCode.BadRequest)
                        }
                    }
                    post("/auth/logout") {
                        val token = call.request.headers[HttpHeaders.Authorization]
                            ?.removePrefix("Bearer ")
                            ?.takeIf { it != call.request.headers[HttpHeaders.Authorization] }
                        if (token != null) withContext(authDispatcher) { authService.logout(token) }
                        call.respond(HttpStatusCode.NoContent)
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
                            call.respondJson(ApiError("invalid_request", "Expected username, password and optional role"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        try {
                            val id = withContext(authDispatcher) { authService.createUser(request.username, request.password, request.role) }
                            call.respondJson(CurrentUserResponse(id, request.username, request.role), HttpStatusCode.Created)
                        } catch (_: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_account", "Username, password or role is invalid"), HttpStatusCode.BadRequest)
                        } catch (failure: java.sql.SQLException) {
                            if (failure.sqlState?.startsWith("23") == true) {
                                call.respondJson(ApiError("account_exists", "An account with that username already exists"), HttpStatusCode.Conflict)
                            } else {
                                throw failure
                            }
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
                            call.respondJson(ApiError("invalid_operation", "Cannot disable the current administrator account"), HttpStatusCode.Conflict)
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
                }
            }
        }
    }
}

data class HealthResponse(val status: String)
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val accessToken: String, val tokenType: String = "Bearer", val expiresAt: String)
data class CurrentUserResponse(val id: Long, val username: String, val role: String)
data class ApiError(val code: String, val message: String)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
data class CreateUserRequest(val username: String, val password: String, val role: String = "USER")
data class SetUserEnabledRequest(val enabled: Boolean)

private const val MAX_PASSWORD_LENGTH = 1_024

private val jsonMapper = jacksonObjectMapper()

private suspend fun ApplicationCall.respondJson(value: Any, status: HttpStatusCode = HttpStatusCode.OK) {
    respondText(jsonMapper.writeValueAsString(value), ContentType.Application.Json, status)
}

fun main(args: Array<String>) {
    val configuration = RuntimeConfigurationLoader().load(args, System.getProperties(), System.getenv(), null, emptyMap())
    val host = configuration.getProperty("airsonic.http.host", "0.0.0.0")
    val port = configuration.getIntProperty("airsonic.http.port", 8080)
    val lifecycle = LifecycleRegistry()
    val database = StandaloneDatabase.connect(configuration)
    lifecycle.registerShutdown("database", database)
    Runtime.getRuntime().addShutdownHook(Thread({ lifecycle.shutdown() }, "airsonic-shutdown"))
    try {
        database.migrate()
    } catch (failure: Throwable) {
        lifecycle.shutdown()
        throw failure
    }
    val authService = LocalAuthService(database.dataSource)
    if (configuration.getBooleanProperty("airsonic.create-admin", false)) {
        try {
            val console = System.console()
                ?: throw IllegalStateException("--airsonic.create-admin requires an interactive terminal")
            val username = console.readLine("Administrator username: ").trim()
            val password = console.readPassword("Administrator password (minimum 12 characters): ")
            val confirmation = console.readPassword("Confirm administrator password: ")
            try {
                require(password.contentEquals(confirmation)) { "Passwords do not match" }
                authService.createInitialAdmin(username, String(password))
                console.printf("Administrator created. Start the server without --airsonic.create-admin.%n")
            } finally {
                password.fill('\u0000')
                confirmation.fill('\u0000')
            }
        } finally {
            lifecycle.shutdown()
        }
        return
    }
    val server = embeddedServer(Netty, host = host, port = port) {
        standaloneModule(authService)
    }
    lifecycle.registerShutdown("ktor-server", AutoCloseable { server.stop(1_000, 5_000) })
    server.start(wait = true)
}
