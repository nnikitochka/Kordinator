package su.nnedition.kord.runtime

import su.nnedition.kord.dao.UserDao
import su.nnedition.kord.security.CredentialEncoderRegistry
import su.nnedition.kord.security.SubsonicCredentialAuthenticationService
import su.nnedition.kord.service.JWTSecurityService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.SecurityService
import su.nnedition.kord.service.SettingsService
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
