package org.airsonic.player.security;

import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserCredential;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Framework-neutral authenticated user data used by credential verification. */
public final class UserIdentity {
    private final String username;
    private final List<UserCredential> credentials;
    private final Set<User.Role> roles;

    public UserIdentity(String username, List<UserCredential> credentials, Set<User.Role> roles) {
        this.username = Objects.requireNonNull(username, "username");
        this.credentials = List.copyOf(Objects.requireNonNull(credentials, "credentials"));
        this.roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
    }

    public String getUsername() {
        return username;
    }

    public List<UserCredential> getCredentials() {
        return credentials;
    }

    public Set<User.Role> getRoles() {
        return roles;
    }
}
