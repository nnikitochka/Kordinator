package org.airsonic.player.security;

import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.security.crypto.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Framework-neutral verifier for Subsonic password and salted-token credentials. */
public final class SubsonicAuthenticator {
    public enum Mode {
        PASSWORD(""), SALTED_TOKEN("salttoken");

        private final String specialization;

        Mode(String specialization) {
            this.specialization = specialization;
        }
    }

    public enum Failure { NO_MATCH, CREDENTIALS_EXPIRED }

    public static final class Result {
        private final UserCredential credential;
        private final boolean upgradeRequired;
        private final Failure failure;

        private Result(UserCredential credential, boolean upgradeRequired, Failure failure) {
            this.credential = credential;
            this.upgradeRequired = upgradeRequired;
            this.failure = failure;
        }

        public Optional<UserCredential> getCredential() { return Optional.ofNullable(credential); }
        public boolean isUpgradeRequired() { return upgradeRequired; }
        public Optional<Failure> getFailure() { return Optional.ofNullable(failure); }
        public boolean isAuthenticated() { return credential != null && failure == null; }
    }

    private final Clock clock;

    public SubsonicAuthenticator() {
        this(Clock.systemUTC());
    }

    SubsonicAuthenticator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The supplied registry is keyed by stored encoder name, without Spring's
     * {id} wrapper. Each matcher must implement that encoder's legacy semantics.
     */
    public Result authenticate(List<UserCredential> credentials, CharSequence presented,
            Mode mode, java.util.Map<String, PasswordEncoder> encoders) {
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(presented, "presented");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(encoders, "encoders");

        for (UserCredential credential : credentials) {
            PasswordEncoder encoder = encoders.get(credential.getEncoder() + mode.specialization);
            if (encoder == null || !encoder.matches(presented, credential.getCredential())) {
                continue;
            }
            Instant expiration = credential.getExpiration();
            if (expiration != null && expiration.isBefore(clock.instant())) {
                return new Result(null, false, Failure.CREDENTIALS_EXPIRED);
            }
            boolean upgrade = mode == Mode.PASSWORD
                    && encoder.upgradeEncoding(credential.getCredential());
            return new Result(credential, upgrade, null);
        }
        return new Result(null, false, Failure.NO_MATCH);
    }
}
