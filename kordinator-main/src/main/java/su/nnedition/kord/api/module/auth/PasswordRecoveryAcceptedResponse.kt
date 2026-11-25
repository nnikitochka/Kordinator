package su.nnedition.kord.api.module.auth

data class PasswordRecoveryAcceptedResponse(
    val message: String = "If an enabled account uses that email, password reset instructions will be sent.",
)
