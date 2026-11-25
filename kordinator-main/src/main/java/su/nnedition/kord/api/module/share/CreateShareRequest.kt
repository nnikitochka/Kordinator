package su.nnedition.kord.api.module.share

data class CreateShareRequest(
    val trackIds: List<Long> = emptyList(),
    val description: String? = null,
    val expiresAt: String? = null,
)
