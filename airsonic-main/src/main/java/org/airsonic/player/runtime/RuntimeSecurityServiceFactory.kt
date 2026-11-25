package org.airsonic.player.runtime

import org.airsonic.player.dao.UserDao
import org.airsonic.player.security.CredentialEncoderRegistry
import org.airsonic.player.security.SubsonicCredentialAuthenticationService
import org.airsonic.player.service.JWTSecurityService
import org.airsonic.player.service.media.MediaFolderService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import javax.cache.CacheManager

/** Builds the runtime authentication and token services. */
object RuntimeSecurityServiceFactory {
    @JvmStatic
    fun createSecurityService(
        userDao: UserDao,
        settingsService: SettingsService,
        mediaFolderService: MediaFolderService,
        cacheManager: CacheManager,
        credentialEncoders: CredentialEncoderRegistry,
    ): SecurityService = SecurityService(
        userDao, settingsService, mediaFolderService, cacheManager, credentialEncoders,
    )

    @JvmStatic
    fun createCredentialAuthenticationService(
        securityService: SecurityService,
        credentialEncoders: CredentialEncoderRegistry,
    ): SubsonicCredentialAuthenticationService =
        SubsonicCredentialAuthenticationService(securityService, credentialEncoders)

    @JvmStatic
    fun createJwtSecurityService(settingsService: SettingsService): JWTSecurityService {
        JWTSecurityService.initializeSigningKey(settingsService)
        return JWTSecurityService(settingsService)
    }
}
