package su.nnedition.kord.api.module.share

data class UpdateShareRequest(
    val description: String? = null,
    val expiresAt: String? = null,
)
