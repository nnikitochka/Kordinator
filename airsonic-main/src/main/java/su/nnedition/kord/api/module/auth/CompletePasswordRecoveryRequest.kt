package su.nnedition.kord.api.module.auth

data class CompletePasswordRecoveryRequest(
    val token: String,
    val newPassword: String,
)
