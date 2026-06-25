package su.nnedition.kord.web.login.model

import kotlinx.serialization.Serializable

@Serializable
class LoginReceiveRemote(
    val login: String,
    val password: String,
)

@Serializable
data class LoginResponseModel(
    val token: String,
)