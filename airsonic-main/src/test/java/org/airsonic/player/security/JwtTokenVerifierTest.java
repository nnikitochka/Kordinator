package org.airsonic.player.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenVerifierTest {
    private static final String KEY = "jwt-verifier-test-key";
    private final JwtTokenVerifier verifier = new JwtTokenVerifier();

    @Test
    void acceptsValidSignedToken() {
        JwtTokenVerifier.Result result = verifier.verify(KEY, token(KEY, Instant.now().plusSeconds(60)));

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getToken().getSubject()).isEqualTo("alice");
        assertThat(result.getFailure()).isNull();
    }

    @Test
    void reportsExpiredTokenSeparately() {
        JwtTokenVerifier.Result result = verifier.verify(KEY, token(KEY, Instant.now().minusSeconds(60)));

        assertThat(result.isVerified()).isFalse();
        assertThat(result.getFailure()).isEqualTo(JwtTokenVerifier.Failure.EXPIRED);
    }

    @Test
    void rejectsWrongKeyMalformedAndMissingTokens() {
        assertThat(verifier.verify("different-key", token(KEY, Instant.now().plusSeconds(60))).getFailure())
                .isEqualTo(JwtTokenVerifier.Failure.INVALID);
        assertThat(verifier.verify(KEY, "not-a-jwt").getFailure())
                .isEqualTo(JwtTokenVerifier.Failure.INVALID);
        assertThat(verifier.verify(KEY, null).getFailure())
                .isEqualTo(JwtTokenVerifier.Failure.INVALID);
    }

    @Test
    void enforcesRequestPathBindingAndRunsAdditionalChecksAfterVerification() {
        String token = token(KEY, Instant.now().plusSeconds(60));
        boolean[] checked = {false};
        verifier.addAdditionalCheck("/ext/stream", decoded -> checked[0] = true);

        JwtTokenVerifier.Result accepted = verifier.verifyRequest(KEY, token, "/ext/stream?id=1&jwt=request-token");
        JwtTokenVerifier.Result rejected = verifier.verifyRequest(KEY, token, "/ext/other?id=1");

        assertThat(accepted.isVerified()).isTrue();
        assertThat(checked[0]).isTrue();
        assertThat(rejected.getFailure()).isEqualTo(JwtTokenVerifier.Failure.PATH_MISMATCH);
    }

    @Test
    void additionalCheckFailurePropagatesForBoundaryMapping() {
        verifier.addAdditionalCheck("/ext/stream", decoded -> {
            throw new IllegalArgumentException("claim rejected");
        });

        assertThatThrownBy(() -> verifier.verifyRequest(KEY, token(KEY, Instant.now().plusSeconds(60)), "/ext/stream?id=1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String token(String key, Instant expiration) {
        return JWT.create().withSubject("alice").withClaim("path", "/ext/stream?id=1")
                .withExpiresAt(Date.from(expiration)).sign(Algorithm.HMAC256(key));
    }
}
