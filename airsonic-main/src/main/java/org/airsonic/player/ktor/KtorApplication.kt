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
                    post("/auth/logout") {
                        val token = call.request.headers[HttpHeaders.Authorization]
                            ?.removePrefix("Bearer ")
                            ?.takeIf { it != call.request.headers[HttpHeaders.Authorization] }
                        if (token != null) withContext(authDispatcher) { authService.logout(token) }
                        call.respond(HttpStatusCode.NoContent)
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
