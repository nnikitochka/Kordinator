package org.airsonic.player.security

import org.airsonic.player.security.LdapDirectoryAuthenticator.DirectoryIdentity
import org.airsonic.player.security.LdapDirectoryAuthenticator.UserNotFoundException
import org.airsonic.player.service.SettingsService
import java.util.Hashtable
import javax.naming.Context
import javax.naming.NamingEnumeration
import javax.naming.NamingException
import javax.naming.directory.DirContext
import javax.naming.directory.InitialDirContext
import javax.naming.directory.SearchControls
import javax.naming.directory.SearchResult

/**
 * LDAP manager-search and user-bind implementation using only the JDK JNDI API.
 * The configured LDAP URL remains the provider URL, including its search base,
 * matching the existing LDAP configuration.
 */
class JndiLdapDirectoryAuthenticator internal constructor(
    private val settings: SettingsService,
    private val contextFactory: ContextFactory
) : LdapDirectoryAuthenticator {
    constructor(settings: SettingsService) : this(settings, ContextFactory(::InitialDirContext))

    internal fun interface ContextFactory {
        @Throws(NamingException::class)
        fun open(environment: Hashtable<String, Any>): DirContext
    }

    override fun authenticate(username: String, password: String): DirectoryIdentity {
        val match = findUser(username)
        val distinguishedName = match.nameInNamespace
        val environment = environment().apply {
            put(Context.SECURITY_PRINCIPAL, distinguishedName)
            put(Context.SECURITY_CREDENTIALS, password)
        }
        var context: DirContext? = null
        try {
            context = contextFactory.open(environment)
            return DirectoryIdentity(username, distinguishedName)
        } catch (e: Exception) {
            throw LdapDirectoryException("LDAP user bind failed", e)
        } finally {
            close(context)
        }
    }

    private fun findUser(username: String): SearchResult {
        val filter = settings.ldapSearchFilter.replace("{0}", escapeFilter(username))
        val controls = SearchControls().apply { searchScope = SearchControls.SUBTREE_SCOPE }
        var context: DirContext? = null
        var results: NamingEnumeration<SearchResult>? = null
        try {
            context = contextFactory.open(environment())
            results = context.search("", filter, controls)
            if (!results.hasMore()) throw UserNotFoundException(username)
            val result = results.next()
            if (results.hasMore()) throw LdapDirectoryException("LDAP search returned multiple users")
            return result
        } catch (e: LdapDirectoryException) {
            throw e
        } catch (e: UserNotFoundException) {
            throw e
        } catch (e: Exception) {
            throw LdapDirectoryException("LDAP search failed", e)
        } finally {
            close(results)
            close(context)
        }
    }

    private fun environment(): Hashtable<String, Any> = Hashtable<String, Any>().apply {
        put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
        put(Context.PROVIDER_URL, settings.ldapUrl)
        put(Context.SECURITY_AUTHENTICATION, "simple")
        val managerDn = settings.ldapManagerDn
        if (!managerDn.isNullOrBlank()) {
            put(Context.SECURITY_PRINCIPAL, managerDn)
            put(Context.SECURITY_CREDENTIALS, settings.ldapManagerPassword)
        }
    }

    class LdapDirectoryException : RuntimeException {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }

    companion object {
        private fun close(enumeration: NamingEnumeration<*>?) {
            if (enumeration == null) return
            try {
                enumeration.close()
            } catch (_: NamingException) {
                // Closing search results is best-effort, matching JNDI's context cleanup.
            }
        }

        private fun close(context: DirContext?) {
            if (context == null) return
            try {
                context.close()
            } catch (_: NamingException) {
                // Closing contexts is best-effort.
            }
        }

        private fun escapeFilter(value: String): String = value
            .replace("\\", "\\5c")
            .replace("*", "\\2a")
            .replace("(", "\\28")
            .replace(")", "\\29")
            .replace("\u0000", "\\00")
    }
}
