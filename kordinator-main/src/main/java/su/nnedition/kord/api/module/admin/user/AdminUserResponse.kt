package su.nnedition.kord.api.module.admin.user

data class AdminUserResponse(val id: Long, val username: String, val role: String, val enabled: Boolean, val email: String? = null)
