package su.nnedition.kord.web.login

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.mindrot.jbcrypt.BCrypt
import su.nnedition.kord.auth.TokenEntity
import su.nnedition.kord.auth.data.local.AuthDatabase
import su.nnedition.kord.web.login.model.LoginReceiveRemote
import su.nnedition.kord.web.login.model.LoginResponseModel
import java.util.UUID

fun Application.configureLoginRouting() {
    val db = AuthDatabase.getOrCreate()

    routing {
        post("/login") {
            val receive = call.receive<LoginReceiveRemote>()

            val user = db.usersDao.findByLogin(receive.login)
            if (user == null || !BCrypt.checkpw(receive.password, user.passwordHash)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid login or password")
                return@post
            }

            val token = UUID.randomUUID().toString()
            db.tokensDao.insertToken(
                TokenEntity(
                    userId = user.id,
                    token = token,
                    expiresAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                )
            )

            call.respond(LoginResponseModel(token))
        }
    }
}