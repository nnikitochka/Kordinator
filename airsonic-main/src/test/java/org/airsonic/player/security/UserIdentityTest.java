package org.airsonic.player.security;

import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserCredential;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserIdentityTest {
    @Test
    void snapshotsCredentialsAndRoles() {
        List<UserCredential> credentials = new ArrayList<>();
        Set<User.Role> roles = new HashSet<>(Set.of(User.Role.STREAM));
        UserIdentity identity = new UserIdentity("alice", credentials, roles);

        credentials.add(new UserCredential("alice", "alice", "secret", "noop", UserCredential.App.AIRSONIC, "test"));
        roles.add(User.Role.ADMIN);

        assertThat(identity.getCredentials()).isEmpty();
        assertThat(identity.getRoles()).containsExactly(User.Role.STREAM);
        assertThatThrownBy(() -> identity.getRoles().add(User.Role.ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullFields() {
        assertThatThrownBy(() -> new UserIdentity(null, List.of(), Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserIdentity("alice", null, Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserIdentity("alice", List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
