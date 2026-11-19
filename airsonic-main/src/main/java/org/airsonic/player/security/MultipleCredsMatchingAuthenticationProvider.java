package org.airsonic.player.security;

import org.airsonic.player.controller.rest.error.APIException;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;

/** Spring boundary for the framework-neutral Subsonic credential verifier. */
public class MultipleCredsMatchingAuthenticationProvider implements AuthenticationProvider {
    public static final String SALT_TOKEN_MECHANISM_SPECIALIZATION = "salttoken";

    private final SubsonicCredentialAuthenticationService authenticationService;

    public MultipleCredsMatchingAuthenticationProvider(
            SubsonicCredentialAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken)) {
            return null;
        }
        if (authentication.getCredentials() == null) {
            throw new BadCredentialsException("Bad credentials");
        }

        boolean saltedToken = authentication instanceof UsernameSaltedTokenAuthenticationToken;
        SubsonicAuthenticator.Mode mode = saltedToken
                ? SubsonicAuthenticator.Mode.SALTED_TOKEN : SubsonicAuthenticator.Mode.PASSWORD;
        String presentedPassword = authentication.getCredentials().toString();
        SubsonicCredentialAuthenticationService.Result result = authenticationService.authenticate(
                authentication.getName(), presentedPassword, mode);

        if (result.getFailure() == SubsonicCredentialAuthenticationService.Failure.INVALID_CREDENTIALS) {
            Throwable cause = saltedToken
                    ? new APIException(ErrorCode.NOT_AUTHENTICATED_UPGRADE_TO_NON_HASHED) : null;
            throw new BadCredentialsException("Bad credentials", cause);
        }
        if (result.getFailure() == SubsonicCredentialAuthenticationService.Failure.CREDENTIALS_EXPIRED) {
            throw new CredentialsExpiredException("User credentials have expired");
        }
        UserIdentity identity = result.getIdentity();

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("IS_AUTHENTICATED_ANONYMOUSLY"));
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        identity.getRoles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name())));
        UsernamePasswordAuthenticationToken authenticated = UsernamePasswordAuthenticationToken.authenticated(
                identity.getUsername(), null, authorities);
        authenticated.setDetails(authentication.getDetails());
        return authenticated;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

}
