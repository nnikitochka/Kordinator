package su.nnedition.kord.security

import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.User

/** Pure authorization decisions over an immutable request caller context. */
class AuthorizationPolicy {
    fun isAuthenticated(caller: CallerContext?): Boolean = caller?.isAuthenticated == true

    fun hasRole(caller: CallerContext?, role: User.Role?): Boolean =
        caller != null && caller.isAuthenticated && caller.hasRole(role)

    /** Preserves the legacy special case where the reserved admin username is administrative. */
    fun isAdmin(caller: CallerContext?): Boolean =
        caller != null && caller.isAuthenticated &&
            (User.USERNAME_ADMIN == caller.username || caller.hasRole(User.Role.ADMIN))

    fun hasFolderAccess(caller: CallerContext?, folderId: Int): Boolean =
        caller != null && caller.isAuthenticated && caller.hasFolderAccess(folderId)

    fun hasFolderAccess(caller: CallerContext?, file: MediaFile?): Boolean =
        file != null && caller != null && hasFolderAccess(caller, file.folderId!!)
}
