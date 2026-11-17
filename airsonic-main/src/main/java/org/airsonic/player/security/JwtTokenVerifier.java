package org.airsonic.player.security;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Objects;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

/** Framework-neutral signature and expiry verification for signed JWTs. */
public final class JwtTokenVerifier {
    private static final String PATH_CLAIM = "path";
    private static final String JWT_PARAMETER = "jwt";
    public enum Failure { EXPIRED, INVALID, PATH_MISMATCH }

    private final Map<String, List<Consumer<DecodedJWT>>> additionalChecks = new HashMap<>();

    public static final class Result {
        private final DecodedJWT token;
        private final Failure failure;

        private Result(DecodedJWT token, Failure failure) {
            this.token = token;
            this.failure = failure;
        }

        public static Result verified(DecodedJWT token) {
            return new Result(Objects.requireNonNull(token, "token"), null);
        }

        public static Result failed(Failure failure) {
            return new Result(null, Objects.requireNonNull(failure, "failure"));
        }

        public boolean isVerified() { return token != null; }
        public DecodedJWT getToken() {
            if (token == null) throw new IllegalStateException("JWT verification failed: " + failure);
            return token;
        }
        public Failure getFailure() { return failure; }
    }

    public Result verify(String signingKey, String rawToken) {
        Objects.requireNonNull(signingKey, "signingKey");
        if (rawToken == null || rawToken.isBlank()) {
            return Result.failed(Failure.INVALID);
        }
        try {
            return Result.verified(JWT.require(Algorithm.HMAC256(signingKey)).build().verify(rawToken));
        } catch (TokenExpiredException expired) {
            return Result.failed(Failure.EXPIRED);
        } catch (RuntimeException invalid) {
            return Result.failed(Failure.INVALID);
        }
    }

    /** Verifies token signature/expiry, request URL binding, then registered claim checks. */
    public Result verifyRequest(String signingKey, String rawToken, String requestedPath) {
        Result result = verify(signingKey, rawToken);
        if (!result.isVerified()) return result;
        DecodedJWT token = result.getToken();
        String claimedPath = token.getClaim(PATH_CLAIM).asString();
        if (!StringUtils.contains(requestedPath, "/WEB-INF/jsp/") && !roughlyEqual(claimedPath, requestedPath)) {
            return Result.failed(Failure.PATH_MISMATCH);
        }
        List<Consumer<DecodedJWT>> checks = additionalChecks.get(parseUri(requestedPath).path);
        if (checks != null) checks.forEach(check -> check.accept(token));
        return result;
    }

    public void addAdditionalCheck(String path, Consumer<DecodedJWT> check) {
        additionalChecks.computeIfAbsent(path, ignored -> new ArrayList<>()).add(check);
    }

    static boolean roughlyEqual(String expectedRaw, String requestedPathRaw) {
        if (StringUtils.isEmpty(expectedRaw)) return false;
        try {
            ParsedUri expected = parseUri(expectedRaw);
            ParsedUri requested = parseUri(requestedPathRaw);
            if (!Objects.equals(expected.path, requested.path)) return false;
            Map<String, List<String>> left = new HashMap<>(expected.queryParams);
            Map<String, List<String>> right = new HashMap<>(requested.queryParams);
            left.remove("size");
            right.remove("size");
            MapDifference<String, List<String>> difference = Maps.difference(left, right);
            return difference.entriesDiffering().isEmpty() || difference.entriesOnlyOnLeft().isEmpty()
                    || (difference.entriesOnlyOnRight().size() == 1
                    && difference.entriesOnlyOnRight().get(JWT_PARAMETER) != null);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static ParsedUri parseUri(String rawUri) {
        if (rawUri == null) throw new IllegalArgumentException("URI must not be null");
        try {
            URI uri = new URI(rawUri);
            return new ParsedUri(uri.getRawPath() == null ? "" : uri.getRawPath(), parseQuery(uri.getRawQuery()));
        } catch (URISyntaxException ex) {
            String withoutFragment = rawUri;
            int fragmentIndex = withoutFragment.indexOf('#');
            if (fragmentIndex >= 0) withoutFragment = withoutFragment.substring(0, fragmentIndex);
            int queryIndex = withoutFragment.indexOf('?');
            String rawPath = queryIndex < 0 ? withoutFragment : withoutFragment.substring(0, queryIndex);
            String rawQuery = queryIndex < 0 ? null : withoutFragment.substring(queryIndex + 1);
            int schemeIndex = rawPath.indexOf(':');
            if (schemeIndex > 0) rawPath = rawPath.substring(schemeIndex + 1);
            if (rawPath.startsWith("//")) {
                int pathIndex = rawPath.indexOf('/', 2);
                rawPath = pathIndex < 0 ? "" : rawPath.substring(pathIndex);
            }
            return new ParsedUri(rawPath, parseQuery(rawQuery));
        }
    }

    private static Map<String, List<String>> parseQuery(String rawQuery) {
        Map<String, List<String>> queryParams = new LinkedHashMap<>();
        if (rawQuery != null) {
            for (String parameter : rawQuery.split("&", -1)) {
                int equalsIndex = parameter.indexOf('=');
                String name = equalsIndex < 0 ? parameter : parameter.substring(0, equalsIndex);
                String value = equalsIndex < 0 ? null : parameter.substring(equalsIndex + 1);
                queryParams.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
            }
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
}
