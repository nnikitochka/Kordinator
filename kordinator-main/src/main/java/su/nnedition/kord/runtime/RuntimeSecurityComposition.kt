package su.nnedition.kord.runtime

import su.nnedition.kord.dao.UserDao
import su.nnedition.kord.security.CredentialEncoderRegistry
import su.nnedition.kord.security.SubsonicCredentialAuthenticationService
import su.nnedition.kord.service.JWTSecurityService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.SecurityService
import su.nnedition.kord.service.SettingsService

/** Lazily assembles security components under the shared runtime lock. */
internal class RuntimeSecurityComposition(private val context: RuntimeCompositionContext) : RuntimeSecurityComponents {
    private var securityService: SecurityService? = null
    private var subsonicCredentialAuthenticationService: SubsonicCredentialAuthenticationService? = null
    private var jwtSecurityService: JWTSecurityService? = null
    private var kordinatorSessionService: su.nnedition.kord.ktor.KordinatorSessionService? = null

    override fun createKordinatorSessionService(
        securityService: SecurityService,
        credentialEncoders: CredentialEncoderRegistry,
        settingsService: SettingsService,
    ): su.nnedition.kord.ktor.KordinatorSessionService = synchronized(context.lock) {
        if (kordinatorSessionService == null) {
            if (context.persistenceGraph == null) {
                throw IllegalStateException("Persistence graph must be created before KordinatorSessionService")
            }
            kordinatorSessionService = su.nnedition.kord.ktor.KordinatorSessionService(
                    securityService, credentialEncoders,
                    su.nnedition.kord.ktor.JdbcKordinatorSessionStore(context.persistenceGraph!!.dataSource),
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
