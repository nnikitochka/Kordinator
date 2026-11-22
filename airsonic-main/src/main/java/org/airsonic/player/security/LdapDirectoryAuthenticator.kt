package org.airsonic.player.security

/** Framework-neutral directory bind operation used by the LDAP authentication boundary. */
fun interface LdapDirectoryAuthenticator {
    fun authenticate(username: String, password: String): DirectoryIdentity

    /** Indicates that the search completed successfully but found no matching account. */
    class UserNotFoundException(username: String) : RuntimeException("LDAP user was not found: $username")

    data class DirectoryIdentity(val username: String, val distinguishedName: String) {
        fun username(): String = username
        fun distinguishedName(): String = distinguishedName
    }
}
