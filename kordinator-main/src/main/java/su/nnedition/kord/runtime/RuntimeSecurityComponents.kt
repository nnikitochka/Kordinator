package su.nnedition.kord.runtime

import su.nnedition.kord.dao.UserDao
import su.nnedition.kord.security.CredentialEncoderRegistry
import su.nnedition.kord.security.SubsonicCredentialAuthenticationService
import su.nnedition.kord.service.JWTSecurityService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.SecurityService
import su.nnedition.kord.service.SettingsService

/** Creation boundary for the security components owned by one runtime. */
interface RuntimeSecurityComponents {
    fun createKordinatorSessionService(
        securityService: SecurityService,
        credentialEncoders: CredentialEncoderRegistry,
        settingsService: SettingsService,
    ): su.nnedition.kord.ktor.KordinatorSessionService

    fun createSecurityService(
        userDao: UserDao,
        settingsService: SettingsService,
        mediaFolderService: MediaFolderService,
        credentialEncoders: CredentialEncoderRegistry,
    ): SecurityService

    fun createSubsonicCredentialAuthenticationService(
        securityService: SecurityService,
        credentialEncoders: CredentialEncoderRegistry,
    ): SubsonicCredentialAuthenticationService

    fun createJwtSecurityService(
        settingsService: SettingsService,
    ): JWTSecurityService
}
