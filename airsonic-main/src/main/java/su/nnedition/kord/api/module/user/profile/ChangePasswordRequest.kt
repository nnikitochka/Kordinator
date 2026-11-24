package su.nnedition.kord.api.module.user.profile

data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
