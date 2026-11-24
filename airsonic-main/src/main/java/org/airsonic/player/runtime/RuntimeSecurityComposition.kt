package org.airsonic.player.runtime

import org.airsonic.player.dao.UserDao
import org.airsonic.player.security.CredentialEncoderRegistry
import org.airsonic.player.security.SubsonicCredentialAuthenticationService
import org.airsonic.player.service.JWTSecurityService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService

/** Lazily assembles security components under the shared runtime lock. */
internal class RuntimeSecurityComposition(private val context: RuntimeCompositionContext) : RuntimeSecurityComponents {
    private var securityService: SecurityService? = null
    private var subsonicCredentialAuthenticationService: SubsonicCredentialAuthenticationService? = null
    private var jwtSecurityService: JWTSecurityService? = null
    private var kordinatorSessionService: org.airsonic.player.ktor.KordinatorSessionService? = null

    override fun createKordinatorSessionService(
        securityService: SecurityService,
        credentialEncoders: CredentialEncoderRegistry,
        settingsService: SettingsService,
    ): org.airsonic.player.ktor.KordinatorSessionService = synchronized(context.lock) {
        if (kordinatorSessionService == null) {
            if (context.persistenceGraph == null) {
                throw IllegalStateException("Persistence graph must be created before KordinatorSessionService")
            }
            kordinatorSessionService = org.airsonic.player.ktor.KordinatorSessionService(
                    securityService, credentialEncoders,
                    org.airsonic.player.ktor.JdbcKordinatorSessionStore(context.persistenceGraph!!.dataSource),
                    settingsService.getNonDecodablePasswordEncoder())
        }
        return@synchronized kordinatorSessionService!!
    }

    override fun createSecurityService(
        userDao: UserDao,
        settingsService: SettingsService,
        mediaFolderService: MediaFolderService,
        credentialEncoders: CredentialEncoderRegistry,
    ): SecurityService = synchronized(context.lock) {
        if (securityService == null) {
            securityService = RuntimeSecurityServiceFactory.createSecurityService(
                    userDao, settingsService, mediaFolderService, context.root.createCacheManager(), credentialEncoders)
        }
        return@synchronized securityService!!
    }

    override fun createSubsonicCredentialAuthenticationService(
        securityService: SecurityService,
        credentialEncoders: CredentialEncoderRegistry,
    ): SubsonicCredentialAuthenticationService = synchronized(context.lock) {
        if (subsonicCredentialAuthenticationService == null) {
            subsonicCredentialAuthenticationService =
                    RuntimeSecurityServiceFactory.createCredentialAuthenticationService(
                            securityService, credentialEncoders)
        }
        return@synchronized subsonicCredentialAuthenticationService!!
    }

    override fun createJwtSecurityService(
        settingsService: SettingsService,
    ): JWTSecurityService = synchronized(context.lock) {
        if (jwtSecurityService == null) {
            jwtSecurityService = RuntimeSecurityServiceFactory.createJwtSecurityService(settingsService)
        }
        return@synchronized jwtSecurityService!!
    }
}
