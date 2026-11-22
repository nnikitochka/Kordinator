package org.airsonic.player.security

import org.airsonic.player.domain.UserCredential
import java.time.Clock
import java.time.Instant
import java.util.Optional
import java.util.Objects

/** Framework-neutral verifier for Subsonic password and salted-token credentials. */
class SubsonicAuthenticator internal constructor(private val clock: Clock) {
    constructor() : this(Clock.systemUTC())

    enum class Mode { PASSWORD, SALTED_TOKEN }

    enum class Failure { NO_MATCH, CREDENTIALS_EXPIRED }

    class Result private constructor(
        private val credentialValue: UserCredential?,
        val isUpgradeRequired: Boolean,
        private val failureValue: Failure?
    ) {
        val credential: Optional<UserCredential> get() = Optional.ofNullable(credentialValue)
        val failure: Optional<Failure> get() = Optional.ofNullable(failureValue)
        val isAuthenticated: Boolean get() = credentialValue != null && failureValue == null

        companion object {
            @JvmStatic
            fun authenticated(credential: UserCredential, upgradeRequired: Boolean) =
                Result(credential, upgradeRequired, null)

            @JvmStatic
            fun failed(failure: Failure) = Result(null, false, failure)
        }
    }

    fun authenticate(
        credentials: List<UserCredential>,
        presented: CharSequence,
        mode: Mode,
        encoders: CredentialEncoderRegistry
    ): Result {
        Objects.requireNonNull(credentials, "credentials")
        Objects.requireNonNull(presented, "presented")
        Objects.requireNonNull(mode, "mode")
        Objects.requireNonNull(encoders, "encoders")

        for (credential in credentials) {
            val specialization = if (mode == Mode.SALTED_TOKEN) "salttoken" else ""
            val encoderName = credential.encoder + specialization
            val matched = try {
                encoders.matches(encoderName, presented, credential.credential)
            } catch (_: IllegalArgumentException) {
                false
            }
            if (!matched) continue

            val expiration: Instant? = credential.expiration
            if (expiration != null && expiration.isBefore(clock.instant())) {
                return Result.failed(Failure.CREDENTIALS_EXPIRED)
            }
            val upgrade = if (mode == Mode.PASSWORD) {
                try {
                    encoders.upgradeEncoding(encoderName, credential.credential)
                } catch (_: IllegalArgumentException) {
                    false
                }
            } else {
                false
            }
            return Result.authenticated(credential, upgrade)
        }
        return Result.failed(Failure.NO_MATCH)
    }
}
