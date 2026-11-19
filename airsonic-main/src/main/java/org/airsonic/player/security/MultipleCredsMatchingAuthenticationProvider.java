package org.airsonic.player.security;

import org.airsonic.player.controller.rest.error.APIException;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.service.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(MultipleCredsMatchingAuthenticationProvider.class);

    private final SecurityService securityService;
    private final CredentialEncoderRegistry credentialEncoders;
    private final SubsonicAuthenticator authenticator = new SubsonicAuthenticator();

    public MultipleCredsMatchingAuthenticationProvider(
            SecurityService securityService, CredentialEncoderRegistry credentialEncoders) {
        this.securityService = securityService;
        this.credentialEncoders = credentialEncoders;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken)) {
            return null;
        }
        if (authentication.getCredentials() == null) {
            throw new BadCredentialsException("Bad credentials");
        }

        UserIdentity identity = securityService.findUserIdentity(authentication.getName(), true);
        if (identity == null) {
            throw new BadCredentialsException("Bad credentials");
        }

        boolean saltedToken = authentication instanceof UsernameSaltedTokenAuthenticationToken;
        SubsonicAuthenticator.Mode mode = saltedToken
                ? SubsonicAuthenticator.Mode.SALTED_TOKEN : SubsonicAuthenticator.Mode.PASSWORD;
        String presentedPassword = authentication.getCredentials().toString();
        SubsonicAuthenticator.Result result = authenticator.authenticate(
                identity.getCredentials(), presentedPassword, mode, credentialEncoders);

        if (!result.isAuthenticated()
                && result.getFailure().orElse(null) != SubsonicAuthenticator.Failure.CREDENTIALS_EXPIRED) {
            Throwable cause = saltedToken
                    ? new APIException(ErrorCode.NOT_AUTHENTICATED_UPGRADE_TO_NON_HASHED) : null;
            throw new BadCredentialsException("Bad credentials", cause);
        }

        if (result.getFailure().orElse(null) == SubsonicAuthenticator.Failure.CREDENTIALS_EXPIRED) {
            throw new CredentialsExpiredException("User credentials have expired");
        }

        UserCredential matchedCredential = result.getCredential().orElseThrow();
        if (result.isUpgradeRequired()) {
            UserCredential upgraded = new UserCredential(matchedCredential);
            upgraded.setCredential(presentedPassword);
            if (!securityService.updateCredentials(matchedCredential, upgraded,
                    upgraded.getComment() + " | Automatically upgraded by system", true)) {
                LOG.debug("Password needs to be upgraded, but failed");
            }
        }

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
