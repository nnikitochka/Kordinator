package su.nnedition.kord.auth

import su.nnedition.kord.web.registration.model.RegisterReceiveRemote

class User(
    val id: Long,
    val username: String,
    val login: String,
    val password: String,
) {
//    companion object {
//        fun new(username: String, login: String, password: String) = User(
//            id = InMemoryCache.createId(),
//            username = username,
//            login = login,
//            password = password,
//        )
//        fun new(request: RegisterReceiveRemote) = User(
//            id = InMemoryCache.createId(),
//            username = request.username,
//            login = request.login,
//            password = request.password,
//        )
//    }
}