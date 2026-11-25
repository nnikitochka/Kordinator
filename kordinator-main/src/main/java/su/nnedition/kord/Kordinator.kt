package su.nnedition.kord

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import su.nnedition.kord.api.configureApiRouting
import su.nnedition.kord.runtime.LifecycleRegistry
import su.nnedition.kord.runtime.RuntimeCompositionRoot
import su.nnedition.kord.runtime.RuntimeConfigurationLoader
import su.nnedition.kord.runtime.StandaloneDatabase
import su.nnedition.kord.security.LocalAuthService
import su.nnedition.kord.security.StandalonePasswordRecoveryService
import su.nnedition.kord.security.LegacyCredentialEncoderRegistry
import su.nnedition.kord.service.library.scan.StandaloneLibraryScanService
import su.nnedition.kord.service.StandaloneArtworkSearchService
import su.nnedition.kord.service.StandaloneArtworkManagementService
import su.nnedition.kord.service.StandalonePlaylistService
import su.nnedition.kord.service.StandalonePlayQueueService
import su.nnedition.kord.service.StandaloneVideoPlaybackService
import su.nnedition.kord.service.jukebox.JukeboxService
import su.nnedition.kord.service.StandaloneFavoriteService
import su.nnedition.kord.service.StandaloneBookmarkService
import su.nnedition.kord.service.StandaloneVideoBookmarkService
import su.nnedition.kord.service.StandaloneTrackNoteService
import su.nnedition.kord.service.StandaloneShareService
import su.nnedition.kord.service.StandaloneAvatarService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.StandaloneRatingService
import su.nnedition.kord.service.StandaloneScrobbleService
import su.nnedition.kord.service.KtorCoreMediaCatalogService

object Kordinator {
    @JvmStatic
    fun main(args: Array<String>) {
        val configuration = RuntimeConfigurationLoader().load(args, System.getProperties(), System.getenv(), null)
        System.setProperty("kordinator.home", configuration.kordinatorHome.toString())
        val host = configuration.getProperty("kordinator.http.host", "0.0.0.0") ?: "0.0.0.0"
        val port = configuration.getIntProperty("kordinator.http.port", 8080)
        val lifecycle = LifecycleRegistry()
        Runtime.getRuntime().addShutdownHook(Thread({ lifecycle.shutdown() }, "kordinator-shutdown"))
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
        if (configuration.getBooleanProperty("kordinator.create-admin", false)) {
            try {
                val console = System.console()
                    ?: throw IllegalStateException("--kordinator.create-admin requires an interactive terminal")
                val username = console.readLine("Administrator username: ").trim()
                val password = console.readPassword("Administrator password (minimum 12 characters): ")
                val confirmation = console.readPassword("Confirm administrator password: ")
                try {
                    require(password.contentEquals(confirmation)) { "Passwords do not match" }
                    authService.createInitialAdmin(username, String(password))
                    console.printf("Administrator created. Start the server without --kordinator.create-admin.%n")
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
        val avatarRoot = SettingsService.getKordinatorHome().resolve("avatars")
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
