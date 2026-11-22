package org.airsonic.player.security

import org.airsonic.player.domain.User
import java.util.Collections
import java.util.Objects

/**
 * Immutable identity and authorization facts for one request.
 *
 * This object is deliberately independent of servlet, Spring Security and
 * coroutine/thread locals. A route may safely pass it across suspension
 * points without changing the identity associated with the request.
 */
class CallerContext(
    username: String?,
    roles: Set<User.Role>?,
    folderIds: Set<Int>?,
    val isAuthenticated: Boolean
) {
    val username: String = Objects.requireNonNull(username, "username")!!
    val roles: Set<User.Role> = immutableCopy(roles, "roles")
    val folderIds: Set<Int> = immutableCopy(folderIds, "folderIds")

    fun hasRole(role: User.Role?): Boolean = role != null && roles.contains(role)

    fun hasFolderAccess(folderId: Int): Boolean = folderIds.contains(folderId)

    companion object {
        private val ANONYMOUS = CallerContext(User.USERNAME_ANONYMOUS, emptySet(), emptySet(), false)

        @JvmStatic
        fun anonymous(): CallerContext = ANONYMOUS

        private fun <T> immutableCopy(values: Set<T>?, name: String): Set<T> {
            val nonNullValues = Objects.requireNonNull(values, name)!!
            return if (nonNullValues.isEmpty()) Collections.emptySet() else java.util.Set.copyOf(nonNullValues)
        }
    }
}
