package su.nnedition.kord.web.info

import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import su.nnedition.kord.Kordinator

fun Application.configureServerInfoRouting() {
    routing {
        post("/server/info") {
            call.respond(ServerInfoModel(
                "0.1",
                Kordinator.uptime,
                Kordinator.config.allowRegistration,
            ))
        }
    }
}