package org.airsonic.player.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.airsonic.player.security.JWTAuthenticationToken;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

public class JWTSecurityService {
    private static final Logger LOG = LoggerFactory.getLogger(JWTSecurityService.class);

    public static final String JWT_PARAM_NAME = "jwt";
    public static final String CLAIM_PATH = "path";

    // TODO make this configurable
    public static final int DEFAULT_DAYS_VALID_FOR = 7;
    private static SecureRandom secureRandom = new SecureRandom();

    private final SettingsService settingsService;

    public JWTSecurityService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public static String generateKey() {
        BigInteger randomInt = new BigInteger(130, secureRandom);
        return randomInt.toString(32);
    }

    public static Algorithm getAlgorithm(String jwtKey) {
        return Algorithm.HMAC256(jwtKey);
    }

    private static String createToken(String jwtKey, String user, String path, Instant expireDate, Map<String, String> additionalClaims) {
        String pathClaim = createPathClaim(path);
        Builder builder = JWT
                .create()
                .withIssuer("airsonic")
                .withSubject(user)
                .withClaim(CLAIM_PATH, pathClaim)
                .withExpiresAt(Date.from(expireDate));

        for (Entry<String, String> claim : additionalClaims.entrySet()) {
            builder = builder.withClaim(claim.getKey(), claim.getValue());
        }

        return builder.sign(getAlgorithm(jwtKey));
    }

    public String addJWTToken(String user, String uri) {
        return addJWTToken(user, uri, null, Collections.emptyMap());
    }

    public String addJWTToken(String user, String uri, Instant expires) {
        return addJWTToken(user, uri, expires, Collections.emptyMap());
    }

    public String addJWTToken(String user, String uri, Map<String, String> additionalClaims) {
        return addJWTToken(user, uri, null, additionalClaims);
    }

    public String addJWTToken(String user, String uri, Instant expires, Map<String, String> additionalClaims) {
        if (expires == null) {
            expires = Instant.now().plus(DEFAULT_DAYS_VALID_FOR, ChronoUnit.DAYS);
        }
        String token = JWTSecurityService.createToken(
                settingsService.getJWTKey(),
                user,
                uri,
                expires,
                additionalClaims);
        return appendQueryParameter(uri, JWT_PARAM_NAME, token);
    }

    private static String createPathClaim(String rawUri) {
        RawUri uri = parseUri(rawUri);
        try {
            // Reconstructing from raw URI components encodes percent signs in values that were
            // already escaped, preserving URL-encoding compatibility.
            URI encoded = new URI(uri.scheme, uri.authority, uri.path, uri.query, null);
            String path = encoded.getRawPath();
            if (path == null) path = "";
            String query = encoded.getRawQuery();
            return path + (!StringUtils.isBlank(query) ? "?" + query : "");
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid URI: " + rawUri, ex);
        }
    }

    private static RawUri parseUri(String rawUri) {
        if (rawUri == null) {
            throw new IllegalArgumentException("URI must not be null");
        }
        try {
            URI uri = new URI(rawUri);
            String path = uri.isOpaque() ? uri.getRawSchemeSpecificPart() : uri.getRawPath();
            return new RawUri(uri.getScheme(), uri.getRawAuthority(), path, uri.getRawQuery());
        } catch (URISyntaxException ex) {
            // Preserve raw pieces here; createPathClaim performs the one encoding pass that
            // Spring's toUriString() performs, including for malformed percent escapes.
            String withoutFragment = rawUri;
            int fragmentIndex = withoutFragment.indexOf('#');
            if (fragmentIndex >= 0) withoutFragment = withoutFragment.substring(0, fragmentIndex);
            int queryIndex = withoutFragment.indexOf('?');
            String rawQuery = queryIndex < 0 ? null : withoutFragment.substring(queryIndex + 1);
            String rawPath = queryIndex < 0 ? withoutFragment : withoutFragment.substring(0, queryIndex);
            String scheme = null;
            int colonIndex = rawPath.indexOf(':');
            if (colonIndex > 0) {
                scheme = rawPath.substring(0, colonIndex);
                rawPath = rawPath.substring(colonIndex + 1);
            }
            String authority = null;
            if (rawPath.startsWith("//")) {
                int authorityEnd = rawPath.indexOf('/', 2);
                authority = authorityEnd < 0 ? rawPath.substring(2) : rawPath.substring(2, authorityEnd);
                rawPath = authorityEnd < 0 ? "" : rawPath.substring(authorityEnd);
            }
            return new RawUri(scheme, authority, rawPath, rawQuery);
        }
    }

    private static final class RawUri {
        private final String scheme;
        private final String authority;
        private final String path;
        private final String query;
        private RawUri(String scheme, String authority, String path, String query) {
            this.scheme = scheme;
            this.authority = authority;
            this.path = path;
            this.query = query;
        }
    }

    private static String appendQueryParameter(String rawUri, String name, String value) {
        int fragmentIndex = rawUri.indexOf('#');
        String beforeFragment = fragmentIndex < 0 ? rawUri : rawUri.substring(0, fragmentIndex);
        String fragment = fragmentIndex < 0 ? "" : rawUri.substring(fragmentIndex);
        int queryIndex = beforeFragment.indexOf('?');
        String separator = queryIndex < 0 ? "?" : queryIndex == beforeFragment.length() - 1 ? "" : "&";
        return beforeFragment + separator + name + "=" + value + fragment;
    }

    public static DecodedJWT verify(String jwtKey, String token) {
        Algorithm algorithm = JWTSecurityService.getAlgorithm(jwtKey);
        JWTVerifier verifier = JWT.require(algorithm).build();
        return verifier.verify(token);
    }

    public DecodedJWT verify(String credentials) {
        return verify(settingsService.getJWTKey(), credentials);
    }

    public static DecodedJWT decode(String token) {
        return JWT.decode(token);
    }

    public static Instant getExpiration(JWTAuthenticationToken auth) {
        return Optional.ofNullable(auth)
                .map(x -> (DecodedJWT) x.getDetails())
                .map(x -> x.getExpiresAt())
                .map(x -> x.toInstant())
                .orElse(null);
    }
}
