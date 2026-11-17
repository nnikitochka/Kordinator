package org.airsonic.player.security;

import org.airsonic.player.domain.User;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable identity and authorization facts for one request.
 *
 * <p>This object is deliberately independent of servlet, Spring Security and
 * coroutine/thread locals. A route may safely pass it across suspension
 * points without changing the identity associated with the request.</p>
 */
public final class CallerContext {
    private static final CallerContext ANONYMOUS = new CallerContext(
            User.USERNAME_ANONYMOUS, Set.of(), Set.of(), false);

    private final String username;
    private final Set<User.Role> roles;
    private final Set<Integer> folderIds;
    private final boolean authenticated;

    public CallerContext(String username, Set<User.Role> roles, Set<Integer> folderIds, boolean authenticated) {
        this.username = Objects.requireNonNull(username, "username");
        this.roles = immutableCopy(roles, "roles");
        this.folderIds = immutableCopy(folderIds, "folderIds");
        this.authenticated = authenticated;
    }

    public static CallerContext anonymous() {
        return ANONYMOUS;
    }

    public String getUsername() {
        return username;
    }

    public Set<User.Role> getRoles() {
        return roles;
    }

    public Set<Integer> getFolderIds() {
        return folderIds;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public boolean hasRole(User.Role role) {
        return role != null && roles.contains(role);
    }

    public boolean hasFolderAccess(int folderId) {
        return folderIds.contains(folderId);
    }

    private static <T> Set<T> immutableCopy(Set<T> values, String name) {
        Objects.requireNonNull(values, name);
        if (values.isEmpty()) {
            return Collections.emptySet();
        }
        return Set.copyOf(values);
    }
}
