package org.airsonic.player.security

import org.airsonic.player.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthorizationPolicyTest {
    private val policy = AuthorizationPolicy()

    @Test
    fun deniesAllPrivilegesToAnonymousCaller() {
        val anonymous = CallerContext.anonymous()

        assertThat(policy.isAuthenticated(anonymous)).isFalse()
        assertThat(policy.hasRole(anonymous, User.Role.STREAM)).isFalse()
        assertThat(policy.isAdmin(anonymous)).isFalse()
        assertThat(policy.hasFolderAccess(anonymous, 1)).isFalse()
    }

    @Test
    fun checksRolesAndFoldersFromCallerContextOnly() {
        val caller = CallerContext("alice", setOf(User.Role.STREAM), setOf(3, 8), true)

        assertThat(policy.isAuthenticated(caller)).isTrue()
        assertThat(policy.hasRole(caller, User.Role.STREAM)).isTrue()
        assertThat(policy.hasRole(caller, User.Role.ADMIN)).isFalse()
        assertThat(policy.hasFolderAccess(caller, 3)).isTrue()
        assertThat(policy.hasFolderAccess(caller, 4)).isFalse()
    }

    @Test
    fun preservesReservedAdminUsernameRuleWithoutGrantingFolderAccess() {
        val caller = CallerContext(User.USERNAME_ADMIN, emptySet(), emptySet(), true)

        assertThat(policy.isAdmin(caller)).isTrue()
        assertThat(policy.hasFolderAccess(caller, 1)).isFalse()
    }

    @Test
    fun unauthenticatedReservedNameCannotBecomeAdmin() {
        val caller = CallerContext(User.USERNAME_ADMIN, emptySet(), emptySet(), false)

        assertThat(policy.isAdmin(caller)).isFalse()
    }
}
