package org.airsonic.player.security;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.airsonic.player.service.JWTSecurityService;
import org.apache.commons.lang3.StringUtils;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JWTAuthenticationProvider implements AuthenticationProvider {
    private static final Logger LOG = LoggerFactory.getLogger(JWTAuthenticationProvider.class);

    private final String jwtKey;

    public JWTAuthenticationProvider(String jwtSignAndVerifyKey) {
        this.jwtKey = jwtSignAndVerifyKey;
    }

    private final Map<String, List<VerificationCheck>> additionalChecks = new HashMap<>();

    @Override
    public Authentication authenticate(Authentication auth) throws AuthenticationException {
        JWTAuthenticationToken authentication = (JWTAuthenticationToken) auth;
        if (authentication.getCredentials() == null || !(authentication.getCredentials() instanceof String)) {
            LOG.error("Credentials not present");
            return null;
        }
        String rawToken = (String) auth.getCredentials();
        DecodedJWT token = null;

        try {
            token = JWTSecurityService.verify(jwtKey, rawToken);
        } catch (TokenExpiredException ex) {
            throw new CredentialsExpiredException("Credentials have expired", ex);
        } catch (Exception ex) {
            throw new BadCredentialsException("Error verifying JWT", ex);
        }

        Claim path = token.getClaim(JWTSecurityService.CLAIM_PATH);

        // TODO:AD This is super unfortunate, but not sure there is a better way when using JSP
        if (StringUtils.contains(authentication.getRequestedPath(), "/WEB-INF/jsp/")) {
            LOG.warn("BYPASSING AUTH FOR WEB-INF page");
        } else if (!roughlyEqual(path.asString(), authentication.getRequestedPath())) {
            throw new InsufficientAuthenticationException("Credentials not valid for path " + authentication.getRequestedPath() + ". They are valid for " + path.asString());
        }

        List<VerificationCheck> moreChecks = additionalChecks.get(parseUri(authentication.getRequestedPath()).path);
        if (moreChecks != null) {
            for (VerificationCheck check : moreChecks) {
                check.check(token);
            }
        }

        return new JWTAuthenticationToken(token.getSubject(), rawToken, authentication.getRequestedPath(), JWT_AUTHORITIES, token);
    }

    public static List<GrantedAuthority> JWT_AUTHORITIES = List.of(
            new SimpleGrantedAuthority("IS_AUTHENTICATED_FULLY"),
            new SimpleGrantedAuthority("ROLE_TEMP"));

    private static boolean roughlyEqual(String expectedRaw, String requestedPathRaw) {
        LOG.debug("Comparing expected [{}] vs requested [{}]", expectedRaw, requestedPathRaw);
        if (StringUtils.isEmpty(expectedRaw)) {
            LOG.debug("False: empty expected");
            return false;
        }
        try {
            ParsedUri expected = parseUri(expectedRaw);
            ParsedUri requested = parseUri(requestedPathRaw);

            if (!Objects.equals(expected.path, requested.path)) {
                LOG.debug("False: expected path [{}] does not match requested path [{}]",
                        expected.path, requested.path);
                return false;
            }

            Map<String, List<String>> left = new HashMap<>(expected.queryParams);
            Map<String, List<String>> right = new HashMap<>(requested.queryParams);

            /*
                If the equality test uses the size parameter on the request, it is possible that the equality test will fail because Sonos
                changes the size parameter according to the client.

                All parameters should be removed, but this would require too much retrofit work throughout the code.
             */
            left.remove("size");
            right.remove("size");

            MapDifference<String, List<String>> difference = Maps.difference(left, right);

            if (difference.entriesDiffering().isEmpty() || difference.entriesOnlyOnLeft().isEmpty()
                    || (difference.entriesOnlyOnRight().size() == 1 && difference.entriesOnlyOnRight().get(JWTSecurityService.JWT_PARAM_NAME) != null)) {
                return true;
            }

            LOG.debug("False: expected query params [{}] do not match requested query params [{}]", expected.queryParams, requested.queryParams);
            return false;

        } catch (Exception e) {
            LOG.warn("Exception encountered while comparing paths", e);
            return false;
        }
    }

    private static ParsedUri parseUri(String rawUri) {
        if (rawUri == null) {
            throw new IllegalArgumentException("URI must not be null");
        }

        try {
            URI uri = new URI(rawUri);
            String path = uri.getRawPath();
            return new ParsedUri(path == null ? "" : path, parseQuery(uri.getRawQuery()));
        } catch (URISyntaxException ex) {
            // Spring's URI builder accepts malformed percent escapes in query values. Keep them
            // literal here instead of rejecting otherwise comparable URLs as java.net.URI does.
            String withoutFragment = rawUri;
            int fragmentIndex = withoutFragment.indexOf('#');
            if (fragmentIndex >= 0) {
                withoutFragment = withoutFragment.substring(0, fragmentIndex);
            }
            int queryIndex = withoutFragment.indexOf('?');
            String rawPath = queryIndex < 0 ? withoutFragment : withoutFragment.substring(0, queryIndex);
            String rawQuery = queryIndex < 0 ? null : withoutFragment.substring(queryIndex + 1);
            int schemeIndex = rawPath.indexOf(':');
            if (schemeIndex > 0) {
                rawPath = rawPath.substring(schemeIndex + 1);
            }
            if (rawPath.startsWith("//")) {
                int pathIndex = rawPath.indexOf('/', 2);
                rawPath = pathIndex < 0 ? "" : rawPath.substring(pathIndex);
            }
            return new ParsedUri(rawPath, parseQuery(rawQuery));
        }
    }

    private static Map<String, List<String>> parseQuery(String rawQuery) {
        Map<String, List<String>> queryParams = new LinkedHashMap<>();
        if (rawQuery == null) {
            return queryParams;
        }
        for (String parameter : rawQuery.split("&", -1)) {
            int equalsIndex = parameter.indexOf('=');
            String name = equalsIndex < 0 ? parameter : parameter.substring(0, equalsIndex);
            String value = equalsIndex < 0 ? null : parameter.substring(equalsIndex + 1);
            queryParams.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
        return queryParams;
    }

    private static final class ParsedUri {
        private final String path;
        private final Map<String, List<String>> queryParams;

        private ParsedUri(String path, Map<String, List<String>> queryParams) {
            this.path = path;
            this.queryParams = queryParams;
        }
    }

    public void addAdditionalCheck(String path, VerificationCheck check) {
        additionalChecks.computeIfAbsent(path, k -> new ArrayList<>()).add(check);
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
