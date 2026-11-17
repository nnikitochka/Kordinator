package org.airsonic.player.security;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Objects;

/** Framework-neutral signature and expiry verification for signed JWTs. */
public final class JwtTokenVerifier {
    public enum Failure { EXPIRED, INVALID }

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
}
