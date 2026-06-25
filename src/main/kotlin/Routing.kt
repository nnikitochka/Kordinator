package su.nnedition.kord

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import su.nnedition.kord.data.domain.track.Track

fun Application.configureRouting() {
    routing {
        get("/") {
//            call.respond(Track(42))
        }
        get("/penis") {
            call.respondText("Hello, World!")
        }
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}