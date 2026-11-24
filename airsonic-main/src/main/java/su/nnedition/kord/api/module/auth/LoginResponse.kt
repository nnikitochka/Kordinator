package su.nnedition.kord.api.module.auth

data class LoginResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresAt: String,
)