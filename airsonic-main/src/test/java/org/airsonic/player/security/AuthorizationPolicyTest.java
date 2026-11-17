package org.airsonic.player.security;

import org.airsonic.player.domain.User;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationPolicyTest {
    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    @Test
    void deniesAllPrivilegesToAnonymousCaller() {
        CallerContext anonymous = CallerContext.anonymous();

        assertThat(policy.isAuthenticated(anonymous)).isFalse();
        assertThat(policy.hasRole(anonymous, User.Role.STREAM)).isFalse();
        assertThat(policy.isAdmin(anonymous)).isFalse();
        assertThat(policy.hasFolderAccess(anonymous, 1)).isFalse();
    }

    @Test
    void checksRolesAndFoldersFromCallerContextOnly() {
        CallerContext caller = new CallerContext("alice", Set.of(User.Role.STREAM), Set.of(3, 8), true);

        assertThat(policy.isAuthenticated(caller)).isTrue();
        assertThat(policy.hasRole(caller, User.Role.STREAM)).isTrue();
        assertThat(policy.hasRole(caller, User.Role.ADMIN)).isFalse();
        assertThat(policy.hasFolderAccess(caller, 3)).isTrue();
        assertThat(policy.hasFolderAccess(caller, 4)).isFalse();
    }

    @Test
    void preservesReservedAdminUsernameRuleWithoutGrantingFolderAccess() {
        CallerContext caller = new CallerContext(User.USERNAME_ADMIN, Set.of(), Set.of(), true);

        assertThat(policy.isAdmin(caller)).isTrue();
        assertThat(policy.hasFolderAccess(caller, 1)).isFalse();
    }

    @Test
    void unauthenticatedReservedNameCannotBecomeAdmin() {
        CallerContext caller = new CallerContext(User.USERNAME_ADMIN, Set.of(), Set.of(), false);

        assertThat(policy.isAdmin(caller)).isFalse();
    }
}
