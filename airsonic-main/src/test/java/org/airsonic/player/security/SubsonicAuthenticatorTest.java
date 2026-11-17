package org.airsonic.player.security;

import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.security.crypto.PasswordEncoder;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubsonicAuthenticatorTest {
    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");
    private final SubsonicAuthenticator authenticator = new SubsonicAuthenticator(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void matchesPlainCredentialAndSignalsPasswordUpgrade() {
        PasswordEncoder encoder = encoder("password", true);
        UserCredential credential = credential("noop", "stored", null);

        SubsonicAuthenticator.Result result = authenticator.authenticate(List.of(credential), "password",
                SubsonicAuthenticator.Mode.PASSWORD, Map.of("noop", encoder));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getCredential()).containsSame(credential);
        assertThat(result.isUpgradeRequired()).isTrue();
    }

    @Test
    void wrongPasswordReturnsNoMatch() {
        SubsonicAuthenticator.Result result = authenticator.authenticate(
                List.of(credential("noop", "stored", null)), "wrong", SubsonicAuthenticator.Mode.PASSWORD,
                Map.of("noop", encoder("password", false)));

        assertThat(result.isAuthenticated()).isFalse();
        assertThat(result.getFailure()).contains(SubsonicAuthenticator.Failure.NO_MATCH);
    }

    @Test
    void saltedTokenUsesSpecializedEncoderAndNeverRequestsUpgrade() {
        PasswordEncoder tokenEncoder = encoder("salted-token", true);
        UserCredential credential = credential("noop", "stored", null);

        SubsonicAuthenticator.Result result = authenticator.authenticate(List.of(credential), "salted-token",
                SubsonicAuthenticator.Mode.SALTED_TOKEN, Map.of("noop", encoder("wrong", false), "noopsalttoken", tokenEncoder));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.isUpgradeRequired()).isFalse();
    }

    @Test
    void expiredMatchingCredentialIsReportedAsExpired() {
        UserCredential credential = credential("noop", "stored", NOW.minusSeconds(1));

        SubsonicAuthenticator.Result result = authenticator.authenticate(List.of(credential), "password",
                SubsonicAuthenticator.Mode.PASSWORD, Map.of("noop", encoder("password", false)));

        assertThat(result.getFailure()).contains(SubsonicAuthenticator.Failure.CREDENTIALS_EXPIRED);
    }

    @Test
    void malformedSaltTokenDoesNotMatch() {
        PasswordEncoder malformedTokenEncoder = new SaltedTokenPasswordEncoder(value -> value);
        SubsonicAuthenticator.Result result = authenticator.authenticate(
                List.of(credential("noop", "stored", null)), "malformed", SubsonicAuthenticator.Mode.SALTED_TOKEN,
                Map.of("noopsalttoken", malformedTokenEncoder));

        assertThat(result.getFailure()).contains(SubsonicAuthenticator.Failure.NO_MATCH);
    }

    private static UserCredential credential(String encoder, String value, Instant expiration) {
        return new UserCredential("alice", "alice", value, encoder, UserCredential.App.AIRSONIC, "fixture", expiration);
    }

    private static PasswordEncoder encoder(String expected, boolean upgrade) {
        return new PasswordEncoder() {
            @Override public String encode(CharSequence rawPassword) { return rawPassword.toString(); }
            @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return expected.contentEquals(rawPassword);
            }
            @Override public boolean upgradeEncoding(String encodedPassword) { return upgrade; }
        };
    }
}
