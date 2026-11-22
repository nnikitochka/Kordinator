package org.airsonic.player.ktor

import org.airsonic.player.domain.UserCredential
import org.airsonic.player.security.CredentialEncoderRegistry
import org.airsonic.player.security.UserIdentity
import org.airsonic.player.service.SecurityService
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

data class KordinatorSessionToken(val accessToken: String, val expiresAt: Instant)
data class KordinatorUserPrincipal(val username: String, val roles: Set<String>)

class KordinatorSessionService @JvmOverloads constructor(
    private val securityService: SecurityService,
    private val credentialEncoders: CredentialEncoderRegistry,
    private val sessions: KordinatorSessionStore,
    private val nonDecodablePasswordEncoder: String,
    private val clock: Clock = Clock.systemUTC(),
    private val sessionDuration: Duration = Duration.ofDays(30),
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private val dummyPasswordHash: String by lazy {
        credentialEncoders.encode(nonDecodablePasswordEncoder, "kordinator-authentication-dummy-password")
    }

    init {
        require(!sessionDuration.isNegative && !sessionDuration.isZero) { "Session duration must be positive" }
    }

    fun login(username: String, password: String): KordinatorSessionToken? {
        if (username.isBlank() || password.isEmpty() || password.length > MAX_PASSWORD_LENGTH) return null

        val identity = securityService.findUserIdentity(username, true)
        val user = identity?.let { securityService.getUserByName(it.username) }
        if (identity == null || user == null || user.isLdapAuthenticated) {
            burnPasswordVerification(password)
            return null
        }

        val credential = identity.credentials.firstOrNull { candidate ->
            val matches = try {
                credentialEncoders.matches(candidate.encoder!!, password, candidate.credential!!)
            } catch (_: IllegalArgumentException) {
                false
            }
            matches
        }
        if (credential == null) {
            burnPasswordVerification(password)
            return null
        }
        if (credential.expiration?.let { !it.isAfter(clock.instant()) } == true) return null

        var credentialVersion = identity.credentials.mapNotNull { it.updated }.maxOrNull()
        val preferredEncoder = nonDecodablePasswordEncoder
        if (credential.encoder != preferredEncoder || credentialEncoders.isOpenText(credential.encoder!!)) {
            val upgraded = UserCredential(credential).apply {
                encoder = preferredEncoder
                this.credential = password
            }
            if (!securityService.updateCredentials(credential, upgraded, credential.comment, true)) return null
            credentialVersion = securityService.findUserIdentity(identity.username, true)
                ?.credentials?.mapNotNull { it.updated }?.maxOrNull()
        }

        val tokenBytes = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
        val now = clock.instant()
        val expiresAt = now.plus(sessionDuration)
        sessions.create(identity.username, sha256(token), credentialVersion, now, expiresAt)
        return KordinatorSessionToken(token, expiresAt)
    }

    fun authenticate(token: String?): KordinatorUserPrincipal? {
        if (token.isNullOrBlank() || token.length > MAX_TOKEN_LENGTH) return null
        val now = clock.instant()
        val hash = sha256(token)
        val session = sessions.findActive(hash, now) ?: return null
        val identity = securityService.findUserIdentity(session.username, true)
        val user = identity?.let { securityService.getUserByName(it.username) }
        if (identity == null || user == null || user.isLdapAuthenticated) {
            sessions.revoke(hash)
            return null
        }

        val currentVersion = identity.credentials.mapNotNull { it.updated }.maxOrNull()
        if (session.credentialVersion != currentVersion) {
            sessions.revoke(hash)
            return null
        }
        return KordinatorUserPrincipal(identity.username, user.roles.map { it.name }.toSet())
    }

    fun revoke(token: String?) {
        if (token.isNullOrBlank() || token.length > MAX_TOKEN_LENGTH) return
        sessions.revoke(sha256(token))
    }

    private fun burnPasswordVerification(password: String) {
        try {
            credentialEncoders.matches(nonDecodablePasswordEncoder, password, dummyPasswordHash)
        } catch (_: IllegalArgumentException) {
            // Keep missing-user behavior uniform even if a configured encoder is unavailable.
        }
    }

    private fun sha256(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TOKEN_BYTES = 32
        const val MAX_TOKEN_LENGTH = 128
        const val MAX_PASSWORD_LENGTH = 1_024
    }
}
