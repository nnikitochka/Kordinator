package su.nnedition.kord.web.registration.model

import kotlinx.serialization.Serializable

@Serializable
data class RegisterReceiveRemote(
    val username: String,
    val login: String,
    val password: String,
)

@Serializable
data class RegisterResponseModel(
    val token: String,
)