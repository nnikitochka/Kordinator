package org.airsonic.player.security;

/** Framework-neutral directory bind operation used by the LDAP authentication boundary. */
@FunctionalInterface
public interface LdapDirectoryAuthenticator {
    DirectoryIdentity authenticate(String username, String password);

    record DirectoryIdentity(String username, String distinguishedName) {
    }
}
