package org.airsonic.player.security;

import org.airsonic.player.domain.User;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallerContextTest {
    @Test
    void anonymousContextHasNoIdentityPrivileges() {
        CallerContext context = CallerContext.anonymous();

        assertThat(context.getUsername()).isEqualTo(User.USERNAME_ANONYMOUS);
        assertThat(context.isAuthenticated()).isFalse();
        assertThat(context.getRoles()).isEmpty();
        assertThat(context.getFolderIds()).isEmpty();
        assertThat(context.hasRole(User.Role.STREAM)).isFalse();
        assertThat(context.hasFolderAccess(1)).isFalse();
    }

    @Test
    void exposesOnlyTheRolesAndFoldersProvidedByTheAuthenticator() {
        CallerContext context = new CallerContext(
                "alice",
                Set.of(User.Role.STREAM, User.Role.DOWNLOAD),
                Set.of(2, 7),
                true);

        assertThat(context.isAuthenticated()).isTrue();
        assertThat(context.hasRole(User.Role.STREAM)).isTrue();
        assertThat(context.hasRole(User.Role.ADMIN)).isFalse();
        assertThat(context.hasFolderAccess(2)).isTrue();
        assertThat(context.hasFolderAccess(3)).isFalse();
        assertThat(context.hasRole(null)).isFalse();
    }

    @Test
    void copiesMutableInputSets() {
        Set<User.Role> roles = new HashSet<>(Set.of(User.Role.STREAM));
        Set<Integer> folders = new HashSet<>(Set.of(4));
        CallerContext context = new CallerContext("alice", roles, folders, true);

        roles.add(User.Role.ADMIN);
        folders.add(9);

        assertThat(context.hasRole(User.Role.ADMIN)).isFalse();
        assertThat(context.hasFolderAccess(9)).isFalse();
        assertThatThrownBy(() -> context.getRoles().add(User.Role.ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> context.getFolderIds().add(9))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullIdentityCollections() {
        assertThatThrownBy(() -> new CallerContext("alice", null, Set.of(), true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CallerContext("alice", Set.of(), null, true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CallerContext(null, Set.of(), Set.of(), true))
                .isInstanceOf(NullPointerException.class);
    }
}
