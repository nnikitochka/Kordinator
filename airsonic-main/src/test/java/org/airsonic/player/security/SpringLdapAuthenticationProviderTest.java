package org.airsonic.player.security;

import org.airsonic.player.controller.rest.error.APIException;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringLdapAuthenticationProviderTest {
    @Test
    void authenticatesPasswordAndReturnsAuthoritiesFromLocalUser() {
        Fixture fixture = new Fixture();

        var result = fixture.provider.authenticate(new UsernamePasswordAuthenticationToken("alice", "secret"));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo("alice");
        assertThat(result.getAuthorities()).extracting("authority")
                .contains("ROLE_USER", "ROLE_STREAM", "ROLE_SETTINGS")
                .doesNotContain("ROLE_ADMIN");
    }

    @Test
    void preservesSubsonicSaltedCredentialUpgradeSignal() {
        Fixture fixture = new Fixture();
        UsernameSaltedTokenAuthenticationToken request = new UsernameSaltedTokenAuthenticationToken("alice", "salt", "token");

        assertThatThrownBy(() -> fixture.provider.authenticate(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasRootCauseInstanceOf(APIException.class);
        verifyNoInteractions(fixture.directory);
    }

    @Test
    void rejectsEmptyCredentialsBeforeDirectoryAccess() {
        Fixture fixture = new Fixture();

        assertThatThrownBy(() -> fixture.provider.authenticate(new UsernamePasswordAuthenticationToken("alice", "")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(fixture.directory);
    }


    @Test
    void translatesInvalidDirectoryCredentialsToBadCredentials() {
        Fixture fixture = new Fixture();
        when(fixture.directory.authenticate("alice", "wrong"))
                .thenThrow(new JndiLdapDirectoryAuthenticator.LdapDirectoryException(
                        "LDAP user bind failed", new javax.naming.AuthenticationException("invalid credentials")));

        assertThatThrownBy(() -> fixture.provider.authenticate(new UsernamePasswordAuthenticationToken("alice", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasRootCauseInstanceOf(javax.naming.AuthenticationException.class);
    }

    @Test
    void translatesMissingDirectoryUserToBadCredentials() {
        Fixture fixture = new Fixture();
        when(fixture.directory.authenticate("alice", "secret"))
                .thenThrow(new LdapDirectoryAuthenticator.UserNotFoundException("alice"));

        assertThatThrownBy(() -> fixture.provider.authenticate(new UsernamePasswordAuthenticationToken("alice", "secret")))
                .isInstanceOf(BadCredentialsException.class)
                .hasRootCauseInstanceOf(LdapDirectoryAuthenticator.UserNotFoundException.class);
    }

    @Test
    void missingDirectoryUserDoesNotCreateAutoShadowAccount() {
        Fixture fixture = new Fixture();
        when(fixture.settings.isLdapAutoShadowing()).thenReturn(true);
        when(fixture.security.getUserByName("nobody", false)).thenReturn(null);
        when(fixture.directory.authenticate("nobody", "secret"))
                .thenThrow(new LdapDirectoryAuthenticator.UserNotFoundException("nobody"));

        assertThatThrownBy(() -> fixture.provider.authenticate(new UsernamePasswordAuthenticationToken("nobody", "secret")))
                .isInstanceOf(BadCredentialsException.class);
        verify(fixture.security, never()).createUser(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void translatesDirectoryOutageToServiceFailureInsteadOfCredentialFailure() {
        Fixture fixture = new Fixture();
        when(fixture.directory.authenticate("alice", "secret"))
                .thenThrow(new JndiLdapDirectoryAuthenticator.LdapDirectoryException(
                        "LDAP search failed", new javax.naming.CommunicationException("server unavailable")));

        assertThatThrownBy(() -> fixture.provider.authenticate(new UsernamePasswordAuthenticationToken("alice", "secret")))
                .isInstanceOf(InternalAuthenticationServiceException.class)
                .hasRootCauseInstanceOf(javax.naming.CommunicationException.class);
    }

    private static final class Fixture {
        private final SettingsService settings = mock(SettingsService.class);
        private final SecurityService security = mock(SecurityService.class);
        private final LdapDirectoryAuthenticator directory = mock(LdapDirectoryAuthenticator.class);
        private final SpringLdapAuthenticationProvider provider;

        private Fixture() {
            when(settings.isLdapEnabled()).thenReturn(true);
            when(settings.isLdapAutoShadowing()).thenReturn(false);
            User user = new User("alice", null, true, 0L, 0L, 0L,
                    Set.of(User.Role.STREAM, User.Role.SETTINGS));
            when(security.getUserByName("alice", false)).thenReturn(user);
            when(directory.authenticate("alice", "secret"))
                    .thenReturn(new LdapDirectoryAuthenticator.DirectoryIdentity("alice", "uid=alice"));
            provider = new SpringLdapAuthenticationProvider(new LdapAuthenticator(security, settings, directory));
        }
    }
}
