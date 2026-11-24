package su.nnedition.kord.api.module.admin.user

data class AdminUserDetailsResponse(val user: AdminUserResponse, val libraryIds: List<Long>)
