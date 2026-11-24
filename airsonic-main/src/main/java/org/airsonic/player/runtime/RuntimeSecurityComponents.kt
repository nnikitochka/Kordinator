package org.airsonic.player.runtime

import org.airsonic.player.dao.UserDao
import org.airsonic.player.security.CredentialEncoderRegistry
import org.airsonic.player.security.SubsonicCredentialAuthenticationService
import org.airsonic.player.service.JWTSecurityService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService

/** Creation boundary for the security components owned by one runtime. */
interface RuntimeSecurityComponents {
    fun createKordinatorSessionService(
        securityService: SecurityService,
        credentialEncoders: CredentialEncoderRegistry,
        settingsService: SettingsService,
    ): org.airsonic.player.ktor.KordinatorSessionService

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
