package su.nnedition.kord.api.module.admin.user

data class CreateUserRequest(val username: String, val password: String, val role: String = "USER", val email: String? = null)
