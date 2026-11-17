package org.airsonic.player.security;

import org.airsonic.player.controller.rest.error.APIException;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.service.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class MultipleCredsMatchingAuthenticationProvider extends DaoAuthenticationProvider {
    public static final String SALT_TOKEN_MECHANISM_SPECIALIZATION = "salttoken";
    private SecurityService securityService;
    private final SubsonicAuthenticator authenticator = new SubsonicAuthenticator();

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails,
            UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
        if (authentication.getCredentials() == null) {
            logger.debug("Authentication failed: no credentials provided");

            throw new BadCredentialsException(messages.getMessage("AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"));
        }

        String presentedPassword = authentication.getCredentials().toString();

        String encoderSpecialization = (authentication instanceof UsernameSaltedTokenAuthenticationToken)
                ? SALT_TOKEN_MECHANISM_SPECIALIZATION
                : "";

        if (!SpringUserDetailsAdapter.class.isAssignableFrom(userDetails.getClass())) {
            throw new InternalAuthenticationServiceException("Retrieved user does not match expected class");
        }

        SpringUserDetailsAdapter userDetail = (SpringUserDetailsAdapter) userDetails;

        SubsonicAuthenticator.Mode mode = encoderSpecialization.isEmpty()
                ? SubsonicAuthenticator.Mode.PASSWORD : SubsonicAuthenticator.Mode.SALTED_TOKEN;
        Map<String, org.airsonic.player.security.crypto.PasswordEncoder> encoders = new java.util.HashMap<>();
        GlobalSecurityConfig.ENCODERS.forEach((name, encoder) -> {
            if (encoder instanceof SpringPasswordEncoderAdapter) {
                encoders.put(name, ((SpringPasswordEncoderAdapter) encoder).getDelegate());
            } else {
                encoders.put(name, new org.airsonic.player.security.crypto.PasswordEncoder() {
                    @Override
                    public String encode(CharSequence rawPassword) {
                        return encoder.encode(rawPassword);
                    }

                    @Override
                    public boolean matches(CharSequence rawPassword, String encodedPassword) {
                        return encoder.matches(rawPassword, encodedPassword);
                    }

                    @Override
                    public boolean upgradeEncoding(String encodedPassword) {
                        return encoder.upgradeEncoding(encodedPassword);
                    }
                });
            }
        });
        SubsonicAuthenticator.Result result = authenticator.authenticate(userDetail.getCredentials(),
                presentedPassword, mode, encoders);

        if (!result.isAuthenticated() && result.getFailure().orElse(null) != SubsonicAuthenticator.Failure.CREDENTIALS_EXPIRED) {
            logger.debug("Authentication failed: password does not match any stored values");

            // If failure via tokens, try and signal to automatically upgrade to a non-token (password) method
            // This custom error code exists for DSub client to automatically switch authentication mechanism
            APIException rootCause = null;
            if (encoderSpecialization.equals(SALT_TOKEN_MECHANISM_SPECIALIZATION)) {
                logger.debug("Authentication attempted via hashed password (salted token), can retry with normal means");
                rootCause = new APIException(ErrorCode.NOT_AUTHENTICATED_UPGRADE_TO_NON_HASHED);
            }
            throw new BadCredentialsException(messages.getMessage("AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"), rootCause);
        }

        if (result.getFailure().orElse(null) == SubsonicAuthenticator.Failure.CREDENTIALS_EXPIRED) {
            logger.debug("User account credentials have expired");

            throw new CredentialsExpiredException(messages.getMessage("AbstractUserDetailsAuthenticationProvider.credentialsExpired", "User credentials have expired"));
        }

        // perform upgrade if needed for password-based auth
        Optional<UserCredential> matchedCred = result.getCredential();
        if (result.isUpgradeRequired()) {
            UserCredential upgraded = new UserCredential(matchedCred.orElseThrow());
            upgraded.setCredential(authentication.getCredentials().toString());
            if (!securityService.updateCredentials(matchedCred.orElseThrow(), upgraded, upgraded.getComment() + " | Automatically upgraded by system", true)) {
                logger.debug("Password needs to be upgraded, but failed");
            }
        }
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    @Autowired
    public void setUserDetailsService(UserDetailsService userDetailsService) {
        super.setUserDetailsService(userDetailsService);
    }

    @Override
    @Autowired
    public void setPasswordEncoder(@Lazy PasswordEncoder passwordEncoder) {
        super.setPasswordEncoder(passwordEncoder);
    }
}
