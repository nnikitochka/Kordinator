package su.nnedition.kord.web.registration

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.mindrot.jbcrypt.BCrypt
import su.nnedition.kord.auth.TokenEntity
import su.nnedition.kord.auth.UserEntity
import su.nnedition.kord.auth.data.local.AuthDatabase
import su.nnedition.kord.web.registration.model.RegisterReceiveRemote
import su.nnedition.kord.web.registration.model.RegisterResponseModel
import java.util.UUID

fun Application.configureRegisterRouting() {
    val db = AuthDatabase.getOrCreate()

    routing {
        post("/register") {
            val receive = call.receive<RegisterReceiveRemote>()

            if (db.usersDao.findByLogin(receive.login) != null) {
                call.respond(HttpStatusCode.Conflict, "User with this login already exists")
                return@post
            }

            val passwordHash = BCrypt.hashpw(receive.password, BCrypt.gensalt())
            val userId = db.usersDao.insertUser(
                UserEntity(
                    username = receive.username,
                    login = receive.login,
                    passwordHash = passwordHash
                )
            )

            val token = UUID.randomUUID().toString()
            db.tokensDao.insertToken(
                TokenEntity(
                    userId = userId,
                    token = token,
                    expiresAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                )
            )

            call.respond(RegisterResponseModel(token))
        }
    }
}