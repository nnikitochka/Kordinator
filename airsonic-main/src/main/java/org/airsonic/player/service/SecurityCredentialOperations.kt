package org.airsonic.player.service

import java.time.Instant
import java.util.ArrayList
import java.util.Comparator
import java.util.function.Predicate
import java.util.stream.Collectors
import org.airsonic.player.dao.UserDao
import org.airsonic.player.domain.User
import org.airsonic.player.domain.UserCredential
import org.airsonic.player.domain.UserCredential.App
import org.airsonic.player.security.CredentialEncoderRegistry
import org.slf4j.LoggerFactory

/** Owns credential encoding, migration and validation policy used by SecurityService. */
internal class SecurityCredentialOperations(
    private val userDao: () -> UserDao,
    private val settingsService: () -> SettingsService,
    private val credentialEncoders: CredentialEncoderRegistry,
) {
    fun encodeCredential(encoder: String, credential: String): String = credentialEncoders.encode(encoder, credential)

    private val retainOneAirsonicCredential = Predicate<UserCredential> { credential ->
        val app = credential.app
        App.AIRSONIC != app ||
            userDao().getCredentials(requireNotNull(credential.username), requireNotNull(app)).isNotEmpty()
    }

    fun updateCredentials(
        oldCredentials: UserCredential,
        newCredentials: UserCredential,
        comment: String?,
        reencodePlaintextNewCredentials: Boolean,
    ): Boolean {
        if (newCredentials.encoder != oldCredentials.encoder || reencodePlaintextNewCredentials) {
            if (reencodePlaintextNewCredentials) {
                newCredentials.credential = credentialEncoders.encode(
                    requireNotNull(newCredentials.encoder),
                    requireNotNull(newCredentials.credential),
                )
            } else if (credentialEncoders.isDecodable(requireNotNull(oldCredentials.encoder))) {
                try {
                    val decoded = credentialEncoders.decode(
                        requireNotNull(oldCredentials.encoder),
                        requireNotNull(oldCredentials.credential),
                    )
                    newCredentials.credential = credentialEncoders.encode(
                        requireNotNull(newCredentials.encoder),
                        decoded,
                    )
                } catch (failure: Exception) {
                    LOG.warn("Could not update credentials for user {}", oldCredentials.username, failure)
                    return false
                }
            }
        }

        if (newCredentials != oldCredentials) {
            newCredentials.comment = comment
            newCredentials.updated = Instant.now()
            return userDao().updateCredential(oldCredentials, newCredentials)
        }
        return true
    }

    fun createCredential(credentials: UserCredential): Boolean {
        credentials.credential = credentialEncoders.encode(
            requireNotNull(credentials.encoder),
            requireNotNull(credentials.credential),
        )
        return userDao().createCredential(credentials)
    }

    fun deleteCredential(credentials: UserCredential): Boolean = try {
        userDao().deleteCredential(credentials, retainOneAirsonicCredential)
    } catch (failure: Exception) {
        LOG.info("Can't delete a credential", failure)
        false
    }

    fun getCredentials(username: String, apps: Array<out App>): List<UserCredential> =
        userDao().getCredentials(username, *apps)

    fun getDecodableCredentialsForApps(username: String, apps: Array<out App>): Map<App, UserCredential> =
        getCredentials(username, apps).parallelStream()
            .filter { credential -> credentialEncoders.isDecodable(requireNotNull(credential.encoder)) }
            .filter { credential -> credential.expiration == null || credential.expiration!!.isAfter(Instant.now()) }
            .collect(
                Collectors.groupingByConcurrent(
                    { credential -> requireNotNull(credential.app) },
                    Collectors.collectingAndThen(
                        Collectors.maxBy(Comparator.comparing<UserCredential, Instant> { requireNotNull(it.updated) }),
                        { optional -> requireNotNull(optional.orElse(null)) },
                    ),
                ),
            )

    fun checkDefaultAdminCredentialsPresent(): Boolean =
        userDao().getCredentials(User.USERNAME_ADMIN, App.AIRSONIC).parallelStream()
            .anyMatch { credential ->
                credentialEncoders.matches(
                    requireNotNull(credential.encoder),
                    User.USERNAME_ADMIN,
                    requireNotNull(credential.credential),
                )
            }

    fun checkOpenCredentialsPresent(): Boolean = credentialEncoders.openTextEncoderNames().parallelStream()
        .mapToInt { encoder -> userDao().getCredentialCountByEncoder(encoder)!! }
        .anyMatch { count -> count > 0 }

    fun checkLegacyCredentialsPresent(): Boolean = userDao().getCredentialCountByEncoder("legacy%") != 0

    fun migrateLegacyCredentialsToNonLegacy(useDecodableOnly: Boolean): Boolean {
        val decodableEncoder = settingsService().decodablePasswordEncoder
        val nonDecodableEncoder = if (useDecodableOnly) decodableEncoder else settingsService().nonDecodablePasswordEncoder
        val failures = ArrayList<UserCredential>()

        userDao().getCredentialsByEncoder("legacy%").forEach { credentials ->
            val newCredentials = UserCredential(credentials).apply {
                encoder = if (credentials.app == App.AIRSONIC) nonDecodableEncoder else decodableEncoder
            }
            if (!updateCredentials(
                    credentials,
                    newCredentials,
                    "${credentials.comment} | Migrated to nonlegacy by admin",
                    false,
                )
            ) {
                LOG.warn("System failed to migrate creds created on {} for user {}", credentials.created, credentials.username)
                failures.add(credentials)
            }
        }
        return failures.isEmpty()
    }

    fun preferredPasswordEncoder(nonDecodableAllowed: Boolean): String =
        if (!nonDecodableAllowed || !settingsService().preferNonDecodablePasswords) {
            settingsService().decodablePasswordEncoder
        } else {
            settingsService().nonDecodablePasswordEncoder
        }

    fun decodeCredentials(credentials: UserCredential): String? = try {
        credentialEncoders.decode(requireNotNull(credentials.encoder), requireNotNull(credentials.credential))
    } catch (failure: Exception) {
        LOG.warn("Could not decode credentials for user {}, app {}", credentials.username, credentials.app, failure)
        null
    }

    private companion object {
        val LOG = LoggerFactory.getLogger(SecurityCredentialOperations::class.java)
    }
}
