package org.airsonic.player.security;

import org.airsonic.player.service.SettingsService;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.Hashtable;

/**
 * LDAP manager-search and user-bind implementation using only the JDK JNDI API.
 * The configured LDAP URL remains the provider URL, including its search base,
 * matching the existing Spring LDAP configuration.
 */
public final class JndiLdapDirectoryAuthenticator implements LdapDirectoryAuthenticator {
    private final SettingsService settings;
    private final ContextFactory contextFactory;

    public JndiLdapDirectoryAuthenticator(SettingsService settings) {
        this(settings, InitialDirContext::new);
    }

    JndiLdapDirectoryAuthenticator(SettingsService settings, ContextFactory contextFactory) {
        this.settings = settings;
        this.contextFactory = contextFactory;
    }

    @FunctionalInterface
    interface ContextFactory {
        DirContext open(Hashtable<String, Object> environment) throws NamingException;
    }

    @Override
    public DirectoryIdentity authenticate(String username, String password) {
        SearchResult match = findUser(username);
        String dn = match.getNameInNamespace();
        Hashtable<String, Object> environment = environment();
        environment.put(Context.SECURITY_PRINCIPAL, dn);
        environment.put(Context.SECURITY_CREDENTIALS, password);
        DirContext context = null;
        try {
            context = contextFactory.open(environment);
            return new DirectoryIdentity(username, dn);
        } catch (Exception e) {
            throw new LdapDirectoryException("LDAP user bind failed", e);
        } finally {
            close(context);
        }
    }

    private SearchResult findUser(String username) {
        String filter = settings.getLdapSearchFilter().replace("{0}", escapeFilter(username));
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        DirContext context = null;
        NamingEnumeration<SearchResult> results = null;
        try {
            context = contextFactory.open(environment());
            results = context.search("", filter, controls);
            if (!results.hasMore()) throw new UserNotFoundException(username);
            SearchResult result = results.next();
            if (results.hasMore()) throw new LdapDirectoryException("LDAP search returned multiple users");
            return result;
        } catch (LdapDirectoryException e) {
            throw e;
        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new LdapDirectoryException("LDAP search failed", e);
        } finally {
            close(results);
            close(context);
        }
    }

    private static void close(NamingEnumeration<?> enumeration) {
        if (enumeration == null) return;
        try { enumeration.close(); } catch (NamingException ignored) { }
    }

    private static void close(DirContext context) {
        if (context == null) return;
        try { context.close(); } catch (NamingException ignored) { }
    }

    private Hashtable<String, Object> environment() {
        Hashtable<String, Object> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, settings.getLdapUrl());
        environment.put(Context.SECURITY_AUTHENTICATION, "simple");
        if (settings.getLdapManagerDn() != null && !settings.getLdapManagerDn().isBlank()) {
            environment.put(Context.SECURITY_PRINCIPAL, settings.getLdapManagerDn());
            environment.put(Context.SECURITY_CREDENTIALS, settings.getLdapManagerPassword());
        }
        return environment;
    }

    private static String escapeFilter(String value) {
        return value.replace("\\", "\\5c").replace("*", "\\2a")
                .replace("(", "\\28").replace(")", "\\29").replace("\0", "\\00");
    }

    public static final class LdapDirectoryException extends RuntimeException {
        public LdapDirectoryException(String message) { super(message); }
        public LdapDirectoryException(String message, Throwable cause) { super(message, cause); }
    }
}
