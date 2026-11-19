package org.airsonic.player.security;

import org.airsonic.player.controller.rest.error.APIException;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.SecurityService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.ArrayList;
import java.util.List;

/** Spring Security boundary for the framework-neutral LDAP authentication policy. */
public final class SpringLdapAuthenticationProvider implements AuthenticationProvider {
    private final LdapAuthenticator authenticator;

    public SpringLdapAuthenticationProvider(LdapAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken)) {
            return null;
        }
        Object credentials = authentication.getCredentials();
        if (credentials == null || credentials.toString().isEmpty()) {
            throw new BadCredentialsException("Bad credentials");
        }

        User user;
        try {
            user = authenticator.authenticate(authentication.getName(), credentials.toString(),
                    authentication instanceof UsernameSaltedTokenAuthenticationToken);
        } catch (LdapAuthenticator.LdapAuthenticationException ex) {
            if (ex.getFailure() == LdapAuthenticator.Failure.HASHED_CREDENTIAL) {
                throw new BadCredentialsException(ex.getMessage(),
                        new APIException(ErrorCode.NOT_AUTHENTICATED_UPGRADE_TO_NON_HASHED));
            }
            throw new BadCredentialsException(ex.getMessage(), ex);
        } catch (LdapDirectoryAuthenticator.UserNotFoundException ex) {
            throw new BadCredentialsException("Bad credentials", ex);
        } catch (JndiLdapDirectoryAuthenticator.LdapDirectoryException ex) {
            if (hasNamingAuthenticationCause(ex)) {
                throw new BadCredentialsException("Bad credentials", ex);
            }
            throw new InternalAuthenticationServiceException("LDAP authentication service failed", ex);
        }

        if (user == null) {
            throw new UsernameNotFoundException("LDAP authentication did not resolve a local user");
        }
        return UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), credentials,
                authorities(user));
    }

    private static List<GrantedAuthority> authorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("IS_AUTHENTICATED_ANONYMOUSLY"));
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        user.getRoles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authorities;
    }

    private static boolean hasNamingAuthenticationCause(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof javax.naming.AuthenticationException) return true;
        }
        return false;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
