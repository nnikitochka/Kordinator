package org.airsonic.player.ktor


data class CreateUserRequest(val username: String, val password: String, val role: String = "USER", val email: String? = null)
data class SetUserEmailRequest(val email: String? = null)
data class ResetUserPasswordRequest(val password: String)
data class RenameUserRequest(val username: String)
data class SetUserEnabledRequest(val enabled: Boolean)
data class SetUserRoleRequest(val role: String)
data class SetUserLibrariesRequest(val libraryIds: Set<Long>)
data class CreateLibraryRequest(val name: String, val path: String)
data class UpdateLibraryRequest(val name: String, val path: String? = null)
data class LibraryResponse(val id: Long, val name: String, val path: String, val enabled: Boolean, val createdAt: String, val scanId: String? = null)
data class AvailableLibraryResponse(val id: Long, val name: String)
data class UploadedLibraryFileResponse(val libraryId: Long, val path: String, val sizeBytes: Long)
data class AvatarResponse(val width: Int, val height: Int, val sizeBytes: Int)
