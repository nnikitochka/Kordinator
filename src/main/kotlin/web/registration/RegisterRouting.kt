package su.nnedition.kord.web.registration

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import su.nnedition.kord.web.User
import su.nnedition.kord.web.data.cache.InMemoryCache
import su.nnedition.kord.web.registration.model.RegisterReceiveRemote
import su.nnedition.kord.web.registration.model.RegisterResponseModel
import java.util.UUID

fun Application.configureRegisterRouting() {
    routing {
        post("/register") {
            val receive = call.receive(RegisterReceiveRemote::class)

            val cached = InMemoryCache.users.firstOrNull { it.login == receive.login }
            if (cached != null) {
                call.respond(HttpStatusCode.Conflict, "User with this login already exists")
                return@post
            }

            val token = UUID.randomUUID().toString()
            val user = User.new(receive)
            InMemoryCache.users.add(user)
            InMemoryCache.tokens[user.id] = mutableListOf(token)

            call.respond(RegisterResponseModel(token))
        }
    }
}