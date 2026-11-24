package su.nnedition.kord.api.module.admin.status

data class AdminHealthResponse(val status: String, val components: List<HealthComponentResponse>)
