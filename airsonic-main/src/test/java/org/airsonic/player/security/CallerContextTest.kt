package org.airsonic.player.security

import org.airsonic.player.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CallerContextTest {
    @Test
    fun anonymousContextHasNoIdentityPrivileges() {
        val context = CallerContext.anonymous()

        assertThat(context.username).isEqualTo(User.USERNAME_ANONYMOUS)
        assertThat(context.isAuthenticated).isFalse()
        assertThat(context.roles).isEmpty()
        assertThat(context.folderIds).isEmpty()
        assertThat(context.hasRole(User.Role.STREAM)).isFalse()
        assertThat(context.hasFolderAccess(1)).isFalse()
    }

    @Test
    fun exposesOnlyTheRolesAndFoldersProvidedByTheAuthenticator() {
        val context = CallerContext(
            "alice",
            setOf(User.Role.STREAM, User.Role.DOWNLOAD),
            setOf(2, 7),
            true,
        )

        assertThat(context.isAuthenticated).isTrue()
        assertThat(context.hasRole(User.Role.STREAM)).isTrue()
        assertThat(context.hasRole(User.Role.ADMIN)).isFalse()
        assertThat(context.hasFolderAccess(2)).isTrue()
        assertThat(context.hasFolderAccess(3)).isFalse()
        assertThat(context.hasRole(null)).isFalse()
    }

    @Test
    fun copiesMutableInputSets() {
        val roles = hashSetOf(User.Role.STREAM)
        val folders = hashSetOf(4)
        val context = CallerContext("alice", roles, folders, true)

        roles.add(User.Role.ADMIN)
        folders.add(9)

        assertThat(context.hasRole(User.Role.ADMIN)).isFalse()
        assertThat(context.hasFolderAccess(9)).isFalse()
        assertThatThrownBy { (context.roles as MutableSet<User.Role>).add(User.Role.ADMIN) }
            .isInstanceOf(UnsupportedOperationException::class.java)
        assertThatThrownBy { (context.folderIds as MutableSet<Int>).add(9) }
            .isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun rejectsNullIdentityCollections() {
        assertThatThrownBy { CallerContext("alice", null, emptySet(), true) }
            .isInstanceOf(NullPointerException::class.java)
        assertThatThrownBy { CallerContext("alice", emptySet(), null, true) }
            .isInstanceOf(NullPointerException::class.java)
        assertThatThrownBy { CallerContext(null, emptySet(), emptySet(), true) }
            .isInstanceOf(NullPointerException::class.java)
    }
}
