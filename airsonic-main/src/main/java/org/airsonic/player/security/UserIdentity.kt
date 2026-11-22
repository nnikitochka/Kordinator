package org.airsonic.player.security

import org.airsonic.player.domain.User
import org.airsonic.player.domain.UserCredential
import java.util.Objects

/** Framework-neutral authenticated user data used by credential verification. */
class UserIdentity(username: String, credentials: List<UserCredential>, roles: Set<User.Role>) {
    val username: String = Objects.requireNonNull(username, "username") as String
    val credentials: List<UserCredential> = java.util.List.copyOf(Objects.requireNonNull(credentials, "credentials"))
    val roles: Set<User.Role> = java.util.Set.copyOf(Objects.requireNonNull(roles, "roles"))
}
