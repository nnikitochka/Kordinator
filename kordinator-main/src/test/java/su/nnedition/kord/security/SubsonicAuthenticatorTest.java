package su.nnedition.kord.security;

import su.nnedition.kord.domain.UserCredential;
import su.nnedition.kord.security.crypto.PasswordEncoder;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SubsonicAuthenticatorTest {
    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");
    private final SubsonicAuthenticator authenticator = new SubsonicAuthenticator(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void matchesPlainCredentialAndSignalsPasswordUpgrade() {
        PasswordEncoder encoder = encoder("password", true);
        UserCredential credential = credential("noop", "stored", null);

        SubsonicAuthenticator.Result result = authenticator.authenticate(List.of(credential), "password",
                SubsonicAuthenticator.Mode.PASSWORD, registry(Map.of("noop", encoder)));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getCredential()).containsSame(credential);
        assertThat(result.isUpgradeRequired()).isTrue();
    }

    @Test
    void wrongPasswordReturnsNoMatch() {
        SubsonicAuthenticator.Result result = authenticator.authenticate(
                List.of(credential("noop", "stored", null)), "wrong", SubsonicAuthenticator.Mode.PASSWORD,
                registry(Map.of("noop", encoder("password", false))));

        assertThat(result.isAuthenticated()).isFalse();
        assertThat(result.getFailure()).contains(SubsonicAuthenticator.Failure.NO_MATCH);
    }

    @Test
    void saltedTokenUsesSpecializedEncoderAndNeverRequestsUpgrade() {
        PasswordEncoder tokenEncoder = encoder("salted-token", true);
        UserCredential credential = credential("noop", "stored", null);

        SubsonicAuthenticator.Result result = authenticator.authenticate(List.of(credential), "salted-token",
                SubsonicAuthenticator.Mode.SALTED_TOKEN,
                registry(Map.of("noop", encoder("wrong", false), "noopsalttoken", tokenEncoder)));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.isUpgradeRequired()).isFalse();
    }

    @Test
    void expiredMatchingCredentialIsReportedAsExpired() {
        UserCredential credential = credential("noop", "stored", NOW.minusSeconds(1));

        SubsonicAuthenticator.Result result = authenticator.authenticate(List.of(credential), "password",
                SubsonicAuthenticator.Mode.PASSWORD, registry(Map.of("noop", encoder("password", false))));

        assertThat(result.getFailure()).contains(SubsonicAuthenticator.Failure.CREDENTIALS_EXPIRED);
    }

    @Test
    void malformedSaltTokenDoesNotMatch() {
        PasswordEncoder malformedTokenEncoder = new SaltedTokenPasswordEncoder(value -> value);
        SubsonicAuthenticator.Result result = authenticator.authenticate(
                List.of(credential("noop", "stored", null)), "malformed", SubsonicAuthenticator.Mode.SALTED_TOKEN,
                registry(Map.of("noopsalttoken", malformedTokenEncoder)));

        assertThat(result.getFailure()).contains(SubsonicAuthenticator.Failure.NO_MATCH);
    }

    private static UserCredential credential(String encoder, String value, Instant expiration) {
        return new UserCredential("alice", "alice", value, encoder, UserCredential.App.KORDINATOR, "fixture", expiration);
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

    private static CredentialEncoderRegistry registry(Map<String, PasswordEncoder> encoders) {
        return new CredentialEncoderRegistry() {
            @Override public String encode(String name, CharSequence credential) { return encoders.get(name).encode(credential); }
            @Override public boolean matches(String name, CharSequence credential, String encoded) {
                PasswordEncoder encoder = encoders.get(name);
                return encoder != null && encoder.matches(credential, encoded);
            }
            @Override public boolean upgradeEncoding(String name, String encoded) {
                PasswordEncoder encoder = encoders.get(name);
                return encoder != null && encoder.upgradeEncoding(encoded);
            }
            @Override public String decode(String name, String encoded) { throw new UnsupportedOperationException(); }
            @Override public boolean isDecodable(String name) { return false; }
            @Override public boolean isOpenText(String name) { return false; }
            @Override public Set<String> decodableEncoderNames() { return Set.of(); }
            @Override public Set<String> openTextEncoderNames() { return Set.of(); }
        };
    }
}
