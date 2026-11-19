package org.airsonic.player.ktor

import io.ktor.server.application.Application
import io.ktor.http.ContentType
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.airsonic.player.runtime.LifecycleRegistry
import org.airsonic.player.runtime.RuntimeConfigurationLoader
import org.airsonic.player.runtime.StandaloneDatabase

fun Application.module() {
    routing {
        get("/internal/ktor/ping") {
            call.respondText("pong")
        }
    }
}

/** Minimal new API module used while the application is moving to a standalone Ktor process. */
fun Application.standaloneModule() {
    routing {
        get("/api/v1/health") {
            call.respondText("{\"status\":\"UP\"}", ContentType.Application.Json)
        }
    }
}

fun main(args: Array<String>) {
    val configuration = RuntimeConfigurationLoader().load(args, System.getProperties(), System.getenv(), null, emptyMap())
    val host = configuration.getProperty("airsonic.http.host", "0.0.0.0")
    val port = configuration.getIntProperty("airsonic.http.port", 8080)
    val lifecycle = LifecycleRegistry()
    val database = StandaloneDatabase.connect(configuration)
    lifecycle.registerShutdown("database", database)
    try {
        database.migrate()
    } catch (failure: Throwable) {
        lifecycle.shutdown()
        throw failure
    }
    val server = embeddedServer(Netty, host = host, port = port, module = Application::standaloneModule)
    lifecycle.registerShutdown("ktor-server", AutoCloseable { server.stop(1_000, 5_000) })
    Runtime.getRuntime().addShutdownHook(Thread({ lifecycle.shutdown() }, "airsonic-shutdown"))
    server.start(wait = true)
}
