package org.airsonic.player.security

import org.airsonic.player.domain.UserCredential
import org.airsonic.player.service.SecurityService
import org.slf4j.LoggerFactory

/** Application-owned username/password and Subsonic salted-token authentication. */
class SubsonicCredentialAuthenticationService(
    private val securityService: SecurityService,
    private val credentialEncoders: CredentialEncoderRegistry,
) {
    enum class Failure { INVALID_CREDENTIALS, CREDENTIALS_EXPIRED }

    data class Result(val identity: UserIdentity?, val failure: Failure?) {
        val isAuthenticated: Boolean get() = identity != null && failure == null
    }

    private val authenticator = SubsonicAuthenticator()

    fun authenticate(username: String, presented: String, mode: SubsonicAuthenticator.Mode): Result {
        val identity = securityService.findUserIdentity(username, true)
            ?: return Result(null, Failure.INVALID_CREDENTIALS)
        val verification = authenticator.authenticate(identity.credentials, presented, mode, credentialEncoders)
        if (verification.failure.orElse(null) == SubsonicAuthenticator.Failure.CREDENTIALS_EXPIRED) {
            return Result(null, Failure.CREDENTIALS_EXPIRED)
        }
        if (!verification.isAuthenticated) {
            return Result(null, Failure.INVALID_CREDENTIALS)
        }

        val matchedCredential = verification.credential.orElseThrow()
        if (verification.isUpgradeRequired) {
            val upgraded = UserCredential(matchedCredential).apply { credential = presented }
            if (!securityService.updateCredentials(
                    matchedCredential,
                    upgraded,
                    "${upgraded.comment} | Automatically upgraded by system",
                    true,
                )
            ) {
                LOG.debug("Password needs to be upgraded, but failed")
            }
        }
        return Result(identity, null)
    }

    private companion object {
        val LOG = LoggerFactory.getLogger(SubsonicCredentialAuthenticationService::class.java)
    }
}
