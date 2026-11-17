package org.airsonic.player.security;

/** Framework-neutral directory bind operation used by the LDAP authentication boundary. */
@FunctionalInterface
public interface LdapDirectoryAuthenticator {
    DirectoryIdentity authenticate(String username, String password);

    /** Indicates that the search completed successfully but found no matching account. */
    final class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String username) {
            super("LDAP user was not found: " + username);
        }
    }

    record DirectoryIdentity(String username, String distinguishedName) {
    }
}
