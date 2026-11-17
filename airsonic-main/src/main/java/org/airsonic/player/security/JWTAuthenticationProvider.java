package org.airsonic.player.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;


public class JWTAuthenticationProvider implements AuthenticationProvider {
    private static final Logger LOG = LoggerFactory.getLogger(JWTAuthenticationProvider.class);

    private final String jwtKey;

    public JWTAuthenticationProvider(String jwtSignAndVerifyKey) {
        this.jwtKey = jwtSignAndVerifyKey;
    }

    private final JwtTokenVerifier verifier = new JwtTokenVerifier();

    @Override
    public Authentication authenticate(Authentication auth) throws AuthenticationException {
        JWTAuthenticationToken authentication = (JWTAuthenticationToken) auth;
        if (authentication.getCredentials() == null || !(authentication.getCredentials() instanceof String)) {
            LOG.error("Credentials not present");
            return null;
        }
        String rawToken = (String) auth.getCredentials();
        JwtTokenVerifier.Result verification = verifier.verifyRequest(jwtKey, rawToken,
                authentication.getRequestedPath());
        if (verification.getFailure() == JwtTokenVerifier.Failure.EXPIRED) {
            throw new CredentialsExpiredException("Credentials have expired");
        }
        if (verification.getFailure() == JwtTokenVerifier.Failure.PATH_MISMATCH) {
            throw new InsufficientAuthenticationException("Credentials not valid for requested path " + authentication.getRequestedPath());
        }
        if (!verification.isVerified()) {
            throw new BadCredentialsException("Error verifying JWT");
        }
        DecodedJWT token = verification.getToken();

        return new JWTAuthenticationToken(token.getSubject(), rawToken, authentication.getRequestedPath(), JWT_AUTHORITIES, token);
    }

    public static List<GrantedAuthority> JWT_AUTHORITIES = List.of(
            new SimpleGrantedAuthority("IS_AUTHENTICATED_FULLY"),
            new SimpleGrantedAuthority("ROLE_TEMP"));

    public void addAdditionalCheck(String path, VerificationCheck check) {
        verifier.addAdditionalCheck(path, check::check);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return JWTAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @FunctionalInterface
    public interface VerificationCheck {
        void check(DecodedJWT jwt) throws AuthenticationException;
    }
}
