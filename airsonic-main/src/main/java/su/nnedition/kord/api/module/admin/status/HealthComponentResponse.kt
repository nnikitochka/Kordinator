package su.nnedition.kord.api.module.admin.status

data class HealthComponentResponse(
    val name: String,
    val status: String,
    val details: Map<String, Any?> = emptyMap(),
    val components: List<HealthComponentResponse> = emptyList(),
)
