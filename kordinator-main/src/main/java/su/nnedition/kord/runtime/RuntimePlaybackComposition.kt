package su.nnedition.kord.runtime

import su.nnedition.kord.dao.PlayerDao
import su.nnedition.kord.dao.PlayerDaoPlayQueueFactory
import su.nnedition.kord.dao.TranscodingDao
import su.nnedition.kord.service.AudioScrobblerService
import su.nnedition.kord.service.JukeboxLegacySubsonicService
import su.nnedition.kord.service.JukeboxService
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.PlayerService
import su.nnedition.kord.service.SecurityService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.StatusService
import su.nnedition.kord.service.TranscodingService

/** Lazily assembles playback components under the shared runtime lock. */
internal class RuntimePlaybackComposition(private val context: RuntimeCompositionContext) : RuntimePlaybackComponents {
    private var playerDao: PlayerDao? = null
    private var jukeboxLegacySubsonicService: JukeboxLegacySubsonicService? = null
    private var jukeboxService: JukeboxService? = null
    private var transcodingService: TranscodingService? = null
    private var playerService: PlayerService? = null
    private var playerDaoPlayQueueFactory: PlayerDaoPlayQueueFactory? = null

    @Throws(Exception::class)
    override fun createPlayerDao(): PlayerDao = synchronized(context.lock) {
        if (context.persistenceGraph == null) {
            throw IllegalStateException("Persistence graph must be created before PlayerDao")
        }
        if (playerDao == null) {
            val queueFactory = createPlayerDaoPlayQueueFactory()
            playerDao = PlayerDao(queueFactory, context.persistenceGraph!!.jdbcHelper)
            playerDao!!.register()
        }
        return@synchronized playerDao!!
    }

    override fun createPlayerDaoPlayQueueFactory(): PlayerDaoPlayQueueFactory = synchronized(context.lock) {
        if (playerDaoPlayQueueFactory == null) {
            playerDaoPlayQueueFactory = PlayerDaoPlayQueueFactory(context.root.createMediaFolderService())
        }
        return@synchronized playerDaoPlayQueueFactory!!
    }

    override fun createJukeboxLegacySubsonicService(
        transcodingService: TranscodingService,
        audioScrobblerService: AudioScrobblerService,
        statusService: StatusService,
        settingsService: SettingsService,
        securityService: SecurityService,
        mediaFileService: MediaFileService,
    ): JukeboxLegacySubsonicService = synchronized(context.lock) {
        if (jukeboxLegacySubsonicService == null) {
            jukeboxLegacySubsonicService = RuntimePlaybackServiceFactory.createLegacyJukeboxService(
                    transcodingService, audioScrobblerService, statusService, settingsService,
                    securityService, mediaFileService)
        }
        return@synchronized jukeboxLegacySubsonicService!!
    }

    override fun createJukeboxService(
        legacyJukebox: JukeboxLegacySubsonicService,
    ): JukeboxService = synchronized(context.lock) {
        if (jukeboxService == null) {
            jukeboxService = RuntimePlaybackServiceFactory.createJukeboxService(legacyJukebox)
        }
        return@synchronized jukeboxService!!
    }

    override fun createTranscodingService(
        transcodingDao: TranscodingDao,
        settingsService: SettingsService,
        mediaFolderService: MediaFolderService,
    ): TranscodingService = synchronized(context.lock) {
        return@synchronized createTranscodingService(transcodingDao, settingsService, mediaFolderService, ::requirePlayerService)
    }

    override fun createTranscodingService(
        transcodingDao: TranscodingDao,
        settingsService: SettingsService,
        mediaFolderService: MediaFolderService,
        playerServiceSupplier: java.util.function.Supplier<PlayerService>,
    ): TranscodingService = synchronized(context.lock) {
        if (transcodingService == null) {
            transcodingService = RuntimePlaybackServiceFactory.createTranscodingService(
                    transcodingDao, settingsService, mediaFolderService, playerServiceSupplier)
        }
        return@synchronized transcodingService!!
    }

    private fun requirePlayerService(): PlayerService = synchronized(context.lock) {
        if (playerService == null) {
            throw IllegalStateException("PlayerService must be created before player transcodings are used")
        }
        return@synchronized playerService!!
    }

    override fun createPlayerService(
        playerDao: PlayerDao,
        statusService: StatusService,
        securityService: SecurityService,
        transcodingService: TranscodingService,
    ): PlayerService = synchronized(context.lock) {
        if (playerService == null) {
            playerService = RuntimePlaybackServiceFactory.createPlayerService(
                    playerDao, statusService, securityService, transcodingService)
            playerService!!.init()
        }
        return@synchronized playerService!!
    }
}
