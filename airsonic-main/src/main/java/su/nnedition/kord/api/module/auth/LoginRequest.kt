package su.nnedition.kord.api.module.auth

data class LoginRequest(
    val username: String,
    val password: String,
)