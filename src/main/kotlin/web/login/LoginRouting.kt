package su.nnedition.kord.web.login

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import su.nnedition.kord.web.data.cache.InMemoryCache
import su.nnedition.kord.web.login.model.LoginReceiveRemote
import su.nnedition.kord.web.login.model.LoginResponseModel
import java.util.UUID

fun Application.configureLoginRouting() {
    routing {
        post("/login") {
            val receive = call.receive(LoginReceiveRemote::class)
            val cached = InMemoryCache.users.firstOrNull { it.login == receive.login }
            if (cached != null) {
                val token = UUID.randomUUID().toString()

                InMemoryCache.tokens.computeIfAbsent(cached.id) {
                    mutableListOf()
                }.add(token)
                call.respond(LoginResponseModel(token))
                return@post
            }

            call.respond(HttpStatusCode.BadRequest)
        }
    }
}