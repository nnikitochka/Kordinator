package su.nnedition.kord

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import su.nnedition.kord.api.configureApiRouting
import org.airsonic.player.runtime.LifecycleRegistry
import org.airsonic.player.runtime.RuntimeCompositionRoot
import org.airsonic.player.runtime.RuntimeConfigurationLoader
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.security.StandalonePasswordRecoveryService
import org.airsonic.player.security.LegacyCredentialEncoderRegistry
import org.airsonic.player.service.library.scan.StandaloneLibraryScanService
import org.airsonic.player.service.StandaloneArtworkSearchService
import org.airsonic.player.service.StandaloneArtworkManagementService
import org.airsonic.player.service.StandalonePlaylistService
import org.airsonic.player.service.StandalonePlayQueueService
import org.airsonic.player.service.StandaloneVideoPlaybackService
import org.airsonic.player.service.jukebox.JukeboxService
import org.airsonic.player.service.StandaloneFavoriteService
import org.airsonic.player.service.StandaloneBookmarkService
import org.airsonic.player.service.StandaloneVideoBookmarkService
import org.airsonic.player.service.StandaloneTrackNoteService
import org.airsonic.player.service.StandaloneShareService
import org.airsonic.player.service.StandaloneAvatarService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.StandaloneRatingService
import org.airsonic.player.service.StandaloneScrobbleService
import org.airsonic.player.service.KtorCoreMediaCatalogService

object Kordinator {
    @JvmStatic
    fun main(args: Array<String>) {
        val configuration = RuntimeConfigurationLoader().load(args, System.getProperties(), System.getenv(), null)
        System.setProperty("airsonic.home", configuration.airsonicHome.toString())
        val host = configuration.getProperty("airsonic.http.host", "0.0.0.0") ?: "0.0.0.0"
        val port = configuration.getIntProperty("airsonic.http.port", 8080)
        val lifecycle = LifecycleRegistry()
        Runtime.getRuntime().addShutdownHook(Thread({ lifecycle.shutdown() }, "airsonic-shutdown"))
        val runtime = RuntimeCompositionRoot(configuration, lifecycle)
        var standaloneDatabase: StandaloneDatabase
        try {
            standaloneDatabase = StandaloneDatabase.connect(configuration)
            lifecycle.registerShutdown("standaloneDatabase", standaloneDatabase)
            val legacyDataSource = runtime.createDataSource()
            runtime.migrateDatabase()
            runtime.createPersistenceGraph(legacyDataSource)
            StandaloneDatabase.migrate(standaloneDatabase.dataSource)
        } catch (failure: Throwable) {
            lifecycle.shutdown()
            throw failure
        }
        val authService = LocalAuthService(standaloneDatabase.dataSource)
        val libraryScanService: StandaloneLibraryScanService
        if (configuration.getBooleanProperty("airsonic.create-admin", false)) {
            try {
                val console = System.console()
                    ?: throw IllegalStateException("--airsonic.create-admin requires an interactive terminal")
                val username = console.readLine("Administrator username: ").trim()
                val password = console.readPassword("Administrator password (minimum 12 characters): ")
                val confirmation = console.readPassword("Confirm administrator password: ")
                try {
                    require(password.contentEquals(confirmation)) { "Passwords do not match" }
                    authService.createInitialAdmin(username, String(password))
                    console.printf("Administrator created. Start the server without --airsonic.create-admin.%n")
                } finally {
                    password.fill('\u0000')
                    confirmation.fill('\u0000')
                }
            } finally {
                lifecycle.shutdown()
            }
            return
        }
        val legacyGraph = runtime.createPersistenceGraph(runtime.createDataSource()).daos
        val settingsService = runtime.createSettingsService()
        val mediaFolderService = runtime.createMediaFolderService()
        val mediaCatalogService = KtorCoreMediaCatalogService(runtime.createDataSource(), standaloneDatabase.dataSource)
        val playlistService = StandalonePlaylistService(standaloneDatabase.dataSource, mediaCatalogService)
        val favoriteService = StandaloneFavoriteService(standaloneDatabase.dataSource, mediaCatalogService)
        val playQueueService = StandalonePlayQueueService(standaloneDatabase.dataSource, mediaCatalogService)
        val bookmarkService = StandaloneBookmarkService(standaloneDatabase.dataSource, mediaCatalogService)
        val trackNoteService = StandaloneTrackNoteService(standaloneDatabase.dataSource, mediaCatalogService)
        val passwordRecoveryService = StandalonePasswordRecoveryService(authService, StandalonePasswordRecoveryService.smtp(settingsService))
        val shareService = StandaloneShareService(standaloneDatabase.dataSource, mediaCatalogService)
        val avatarRoot = SettingsService.getAirsonicHome().resolve("avatars")
        val avatarService = StandaloneAvatarService(avatarRoot.resolve("accounts")).also {
            it.migrateLegacyAvatars(runtime.createDataSource(), standaloneDatabase.dataSource, avatarRoot)
        }
        val healthContributors = runtime.createHealthContributorRegistry(runtime.createDataSource())
        val healthStatusAggregator = runtime.createStatusAggregator()
        val pathWatcherService = runtime.createPathWatcherService()
        val taskSchedulingService = runtime.createTaskSchedulingService()
        val databaseService = runtime.createDatabaseService(settingsService, legacyGraph.databaseDao, taskSchedulingService)
        val cacheManager = runtime.createCacheManager()
        val meterRegistry = runtime.createMeterRegistry()
        val versionService = runtime.createVersionService()
        val serverInfoService = runtime.createServerInfoService(versionService, settingsService)
        val ratingService = StandaloneRatingService(standaloneDatabase.dataSource, mediaCatalogService)
        val securityService = runtime.createSecurityService(
            legacyGraph.userDao,
            settingsService,
            mediaFolderService,
            LegacyCredentialEncoderRegistry(settingsService),
        )
        val coverArtService = runtime.createCoverArtService(legacyGraph.coverArtDao)
        val mediaFileService = runtime.createMediaFileService(
            securityService,
            settingsService,
            legacyGraph.mediaFileDao,
            legacyGraph.albumDao,
            runtime.createJaudiotaggerParser(),
            runtime.createMetaDataParserFactory(),
            coverArtService,
        )
        val artworkSearchService = StandaloneArtworkSearchService()
        val artworkManagementService = StandaloneArtworkManagementService(mediaFileService, mediaFolderService, coverArtService)
        val tagEditService = runtime.createTagEditService(
            runtime.createMetaDataParserFactory(), mediaFileService, securityService,
        )
        val statusService = runtime.createStatusService(
            mediaFileService,
            mediaFolderService,
            settingsService,
            runtime.createTaskSchedulingService(),
        )
        val transcodingService = runtime.createTranscodingService(legacyGraph.transcodingDao, settingsService, mediaFolderService)
        val videoPlaybackService = StandaloneVideoPlaybackService(mediaFileService, transcodingService)
        val videoBookmarkService = StandaloneVideoBookmarkService(standaloneDatabase.dataSource, mediaFileService)
        val playerService = runtime.createPlayerService(runtime.createPlayerDao(), statusService, securityService, transcodingService)
        val audioScrobblerService = runtime.createAudioScrobblerService(settingsService, securityService)
        val scrobbleService = StandaloneScrobbleService(
            mediaFileService,
            statusService,
            audioScrobblerService,
        )
        val lyricsService = runtime.createLyricsService()
        val searchIndex = runtime.createIndexManager()
        val randomSearchService = runtime.createSearchService(mediaFileService)
        val topSongsService = runtime.createLastFmService(legacyGraph.mediaFileDao, mediaFileService, legacyGraph.artistDao)
        val captionService = runtime.createCaptionService(
            mediaFileService,
            mediaFolderService,
            settingsService,
            runtime.createMetaDataParserFactory(),
        )
        val corePlaylistService = runtime.createPlaylistService(securityService, settingsService, mediaFileService, coverArtService)
        val legacyJukeboxEngine = runtime.createJukeboxLegacySubsonicService(
            transcodingService,
            audioScrobblerService,
            statusService,
            settingsService,
            securityService,
            mediaFileService,
        )
        val legacyJukeboxService = runtime.createJukeboxService(legacyJukeboxEngine)
        val jukeboxService = JukeboxService(mediaFolderService, legacyJukeboxEngine)
        val upnpContentDirectory = runtime.createFolderBasedContentDirectory(
            mediaFileService,
            mediaFolderService,
            corePlaylistService,
            searchIndex,
            settingsService,
            playerService,
            transcodingService,
            runtime.createJwtSecurityService(settingsService),
        )
        val upnpService = runtime.createUpnpService(settingsService, upnpContentDirectory)
        val generalSettingsService = runtime.createGeneralSettingsAdministrationService(settingsService, corePlaylistService)
        val mediaScannerService = runtime.createMediaScannerService(
            corePlaylistService,
            settingsService,
            searchIndex,
            mediaFileService,
            mediaFolderService,
            coverArtService,
        )
        val musicFolderAdministrationService = runtime.createMusicFolderAdministrationService(
            settingsService,
            mediaScannerService,
            mediaFolderService,
            legacyGraph.artistDao,
            legacyGraph.albumDao,
            legacyGraph.mediaFileDao,
            searchIndex,
            corePlaylistService,
            coverArtService,
        )
        libraryScanService = StandaloneLibraryScanService(
            standaloneDatabase.dataSource,
            mediaFolderService,
            mediaFileService,
            mediaScannerService,
        )
        val server = embeddedServer(Netty, host = host, port = port) {
            configureApiRouting(
                authService,
                libraryScanService,
                playlistService,
                favoriteService,
                mediaFolderService,
                mediaCatalogService,
                playQueueService,
                bookmarkService,
                ratingService,
                scrobbleService,
                lyricsService,
                randomSearchService,
                settingsService.getGenreSeparators(),
                topSongsService,
                mediaFileService,
                legacyGraph.artistDao,
                legacyGraph.mediaFileDao,
                captionService,
                shareService,
                avatarService,
                healthContributors,
                healthStatusAggregator,
                pathWatcherService,
                taskSchedulingService,
                cacheManager,
                meterRegistry,
                statusService,
                securityService,
                serverInfoService,
                settingsService,
                generalSettingsService,
                transcodingService,
                upnpService,
                musicFolderAdministrationService,
                playerService,
                legacyJukeboxService,
                databaseService,
                videoPlaybackService,
                jukeboxService,
                trackNoteService,
                passwordRecoveryService,
                tagEditService,
                artworkSearchService,
                artworkManagementService,
                videoBookmarkService,
            )
        }
        lifecycle.registerShutdown("ktor-server") { server.stop(1_000, 5_000) }
        server.start(wait = true)
    }
}
