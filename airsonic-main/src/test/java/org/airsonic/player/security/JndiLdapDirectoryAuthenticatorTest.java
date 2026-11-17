package org.airsonic.player.security;

import org.airsonic.player.service.SettingsService;
import org.junit.jupiter.api.Test;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JndiLdapDirectoryAuthenticatorTest {
    private static final String LDAP_URL = "ldap://directory.example.test:389/ou=people,dc=example,dc=org";
    private final SettingsService settings = mock(SettingsService.class);

    @Test
    void searchesConfiguredBaseWithEscapedFilterThenBindsAsMatchedUser() {
        when(settings.getLdapUrl()).thenReturn(LDAP_URL);
        when(settings.getLdapManagerDn()).thenReturn("cn=manager,dc=example,dc=org");
        when(settings.getLdapManagerPassword()).thenReturn("manager-secret");
        when(settings.getLdapSearchFilter()).thenReturn("(uid={0})");
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean searchClosed = new AtomicBoolean();
        AtomicBoolean bindClosed = new AtomicBoolean();
        List<Map<String, Object>> environments = new ArrayList<>();
        List<String> searchArguments = new ArrayList<>();
        SearchResult result = searchResult("uid=alice,ou=people,dc=example,dc=org");

        JndiLdapDirectoryAuthenticator authenticator = new JndiLdapDirectoryAuthenticator(settings, environment -> {
            environments.add(Map.copyOf(environment));
            if (calls.getAndIncrement() == 0) {
                return context((method, args) -> {
                    if (method.getName().equals("search")) {
                        searchArguments.add((String) args[0]);
                        searchArguments.add((String) args[1]);
                        SearchControls controls = (SearchControls) args[2];
                        searchArguments.add(String.valueOf(controls.getSearchScope()));
                        return enumeration(List.of(result));
                    }
                    if (method.getName().equals("close")) searchClosed.set(true);
                    return defaultValue(method);
                });
            }
            assertThat(environment.get("java.naming.security.principal"))
                    .isEqualTo("uid=alice,ou=people,dc=example,dc=org");
            assertThat(environment.get("java.naming.security.credentials")).isEqualTo("user-secret");
            DirContext bind = context((method, args) -> {
                if (method.getName().equals("close")) bindClosed.set(true);
                return defaultValue(method);
            });
            return bind;
        });

        LdapDirectoryAuthenticator.DirectoryIdentity identity = authenticator.authenticate("alice\\*(x)\0", "user-secret");

        assertThat(identity.username()).isEqualTo("alice\\*(x)\0");
        assertThat(identity.distinguishedName()).isEqualTo("uid=alice,ou=people,dc=example,dc=org");
        assertThat(searchArguments).containsExactly("", "(uid=alice\\5c\\2a\\28x\\29\\00)",
                String.valueOf(SearchControls.SUBTREE_SCOPE));
        assertThat(environments).hasSize(2);
        assertThat(environments.get(0)).containsEntry("java.naming.provider.url", LDAP_URL)
                .containsEntry("java.naming.security.authentication", "simple")
                .containsEntry("java.naming.security.principal", "cn=manager,dc=example,dc=org")
                .containsEntry("java.naming.security.credentials", "manager-secret");
        assertThat(environments.get(1)).containsEntry("java.naming.provider.url", LDAP_URL)
                .containsEntry("java.naming.security.authentication", "simple")
                .containsEntry("java.naming.security.principal", "uid=alice,ou=people,dc=example,dc=org")
                .containsEntry("java.naming.security.credentials", "user-secret");
        assertThat(searchClosed).isTrue();
        assertThat(bindClosed).isTrue();
    }

    @Test
    void closesSearchContextWhenNoUserMatches() {
        when(settings.getLdapUrl()).thenReturn(LDAP_URL);
        when(settings.getLdapSearchFilter()).thenReturn("(uid={0})");
        AtomicBoolean closed = new AtomicBoolean();
        JndiLdapDirectoryAuthenticator authenticator = new JndiLdapDirectoryAuthenticator(settings,
                environment -> context((method, args) -> {
                    if (method.getName().equals("search")) return enumeration(List.of());
                    if (method.getName().equals("close")) closed.set(true);
                    return defaultValue(method);
                }));

        assertThatThrownBy(() -> authenticator.authenticate("nobody", "secret"))
                .isInstanceOf(LdapDirectoryAuthenticator.UserNotFoundException.class)
                .hasMessage("LDAP user was not found: nobody");
        assertThat(closed).isTrue();
    }

    @Test
    void rejectsAmbiguousSearchAndClosesSearchContext() {
        when(settings.getLdapUrl()).thenReturn(LDAP_URL);
        when(settings.getLdapSearchFilter()).thenReturn("(uid={0})");
        AtomicBoolean closed = new AtomicBoolean();
        JndiLdapDirectoryAuthenticator authenticator = new JndiLdapDirectoryAuthenticator(settings,
                environment -> context((method, args) -> {
                    if (method.getName().equals("search")) return enumeration(List.of(
                            searchResult("uid=alice,ou=people,dc=example,dc=org"),
                            searchResult("uid=alice,ou=contractors,dc=example,dc=org")));
                    if (method.getName().equals("close")) closed.set(true);
                    return defaultValue(method);
                }));

        assertThatThrownBy(() -> authenticator.authenticate("alice", "secret"))
                .isInstanceOf(JndiLdapDirectoryAuthenticator.LdapDirectoryException.class)
                .hasMessage("LDAP search returned multiple users");
        assertThat(closed).isTrue();
    }

    @Test
    void mapsDirectorySearchFailureAndClosesContext() {
        when(settings.getLdapUrl()).thenReturn(LDAP_URL);
        when(settings.getLdapSearchFilter()).thenReturn("(uid={0})");
        AtomicBoolean closed = new AtomicBoolean();
        JndiLdapDirectoryAuthenticator authenticator = new JndiLdapDirectoryAuthenticator(settings,
                environment -> context((method, args) -> {
                    if (method.getName().equals("search")) throw new NamingException("offline");
                    if (method.getName().equals("close")) closed.set(true);
                    return defaultValue(method);
                }));

        assertThatThrownBy(() -> authenticator.authenticate("alice", "secret"))
                .isInstanceOf(JndiLdapDirectoryAuthenticator.LdapDirectoryException.class)
                .hasMessage("LDAP search failed")
                .hasCauseInstanceOf(NamingException.class);
        assertThat(closed).isTrue();
    }


    @Test
    void wrapsUserBindFailureAndStillClosesSearchContext() {
        when(settings.getLdapUrl()).thenReturn(LDAP_URL);
        when(settings.getLdapSearchFilter()).thenReturn("(uid={0})");
        AtomicBoolean searchClosed = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();
        JndiLdapDirectoryAuthenticator authenticator = new JndiLdapDirectoryAuthenticator(settings, environment -> {
            if (calls.getAndIncrement() == 0) {
                return context((method, args) -> {
                    if (method.getName().equals("search")) return enumeration(List.of(
                            searchResult("uid=alice,ou=people,dc=example,dc=org")));
                    if (method.getName().equals("close")) searchClosed.set(true);
                    return defaultValue(method);
                });
            }
            throw new javax.naming.AuthenticationException("invalid credentials");
        });

        assertThatThrownBy(() -> authenticator.authenticate("alice", "wrong"))
                .isInstanceOf(JndiLdapDirectoryAuthenticator.LdapDirectoryException.class)
                .hasMessage("LDAP user bind failed")
                .hasCauseInstanceOf(javax.naming.AuthenticationException.class);
        assertThat(searchClosed).isTrue();
    }

    private static SearchResult searchResult(String dn) {
        return new SearchResult(dn, null, null, new BasicAttributes(), false) {
            @Override
            public String getNameInNamespace() {
                return dn;
            }
        };
    }

    private static NamingEnumeration<SearchResult> enumeration(List<SearchResult> values) {
        Iterator<SearchResult> iterator = values.iterator();
        return new NamingEnumeration<>() {
            @Override public SearchResult next() { return iterator.next(); }
            @Override public boolean hasMore() { return iterator.hasNext(); }
            @Override public void close() { }
            @Override public boolean hasMoreElements() { return iterator.hasNext(); }
            @Override public SearchResult nextElement() { return iterator.next(); }
        };
    }

    private static DirContext context(ContextCall call) {
        return (DirContext) Proxy.newProxyInstance(DirContext.class.getClassLoader(),
                new Class<?>[] {DirContext.class}, (proxy, method, args) -> call.invoke(method, args));
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    @FunctionalInterface
    private interface ContextCall {
        Object invoke(Method method, Object[] args) throws Throwable;
    }
}
