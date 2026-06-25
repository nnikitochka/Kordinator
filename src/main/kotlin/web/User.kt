package su.nnedition.kord.web

import io.ktor.http.cio.Request
import su.nnedition.kord.web.data.cache.InMemoryCache.createId
import su.nnedition.kord.web.registration.model.RegisterReceiveRemote

class User(
    val id: Long,
    val username: String,
    val login: String,
    val password: String,
) {
    companion object {
        fun new(username: String, login: String, password: String) = User(
            id = createId(),
            username = username,
            login = login,
            password = password,
        )
        fun new(request: RegisterReceiveRemote) = User(
            id = createId(),
            username = request.username,
            login = request.login,
            password = request.password,
        )
    }
}