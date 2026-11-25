package su.nnedition.kord.api

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.MeterRegistry
import javax.cache.CacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.health.HealthContributorRegistry
import su.nnedition.kord.health.StatusAggregator
import su.nnedition.kord.security.LocalAuthService
import su.nnedition.kord.security.LoginRateLimiter
import su.nnedition.kord.security.StandalonePasswordRecoveryService
import su.nnedition.kord.service.ArtworkManagementService
import su.nnedition.kord.service.CaptionService
import su.nnedition.kord.service.DatabaseService
import su.nnedition.kord.service.GeneralSettingsAdministrationService
import su.nnedition.kord.service.JukeboxService
import su.nnedition.kord.service.KtorCoreMediaCatalogService
import su.nnedition.kord.service.LastFmService
import su.nnedition.kord.service.LyricsService
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.MusicFolderAdministrationService
import su.nnedition.kord.service.PathWatcherService
import su.nnedition.kord.service.PlayerService
import su.nnedition.kord.service.SearchService
import su.nnedition.kord.service.SecurityService
import su.nnedition.kord.service.ServerInfoService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.StandaloneArtworkSearchService
import su.nnedition.kord.service.StandaloneAudioTranscodingService
import su.nnedition.kord.service.StandaloneAvatarService
import su.nnedition.kord.service.StandaloneBookmarkService
import su.nnedition.kord.service.StandaloneFavoriteService
import su.nnedition.kord.service.library.scan.StandaloneLibraryScanService
import su.nnedition.kord.service.StandalonePlayQueueService
import su.nnedition.kord.service.StandalonePlaylistService
import su.nnedition.kord.service.StandaloneRatingService
import su.nnedition.kord.service.StandaloneScrobbleService
import su.nnedition.kord.service.StandaloneShareService
import su.nnedition.kord.service.StandaloneTrackNoteService
import su.nnedition.kord.service.StandaloneVideoBookmarkService
import su.nnedition.kord.service.StandaloneVideoPlaybackService
import su.nnedition.kord.service.StatusService
import su.nnedition.kord.service.TagEditService
import su.nnedition.kord.service.TaskSchedulingService
import su.nnedition.kord.service.TranscodingService
import su.nnedition.kord.service.UPnPService
import su.nnedition.kord.api.module.admin.library.adminLibraryRoutes
import su.nnedition.kord.api.module.admin.library.libraryScanRoutes
import su.nnedition.kord.api.module.admin.player.adminPlayerRoutes
import su.nnedition.kord.api.module.admin.database.databaseRoutes
import su.nnedition.kord.api.module.admin.settings.adminSettingsRoutes
import su.nnedition.kord.api.module.admin.status.adminStatusRoutes
import su.nnedition.kord.api.module.admin.user.adminUserLibrariesRoutes
import su.nnedition.kord.api.module.admin.user.adminUserManagementRoutes
import su.nnedition.kord.api.module.admin.user.adminUserRoutes
import su.nnedition.kord.api.module.album.albumRoutes
import su.nnedition.kord.api.module.artist.artistRoutes
import su.nnedition.kord.api.module.artwork.artworkRoutes
import su.nnedition.kord.api.module.auth.authRoutes
import su.nnedition.kord.api.module.catalog.catalogRoutes
import su.nnedition.kord.api.module.favorite.favoriteRoutes
import su.nnedition.kord.api.module.media.mediaRoutes
import su.nnedition.kord.api.module.metadata.metadataRoutes
import su.nnedition.kord.api.module.playlist.playlistRoutes
import su.nnedition.kord.api.module.public.publicRoutes
import su.nnedition.kord.api.module.search.searchRoutes
import su.nnedition.kord.api.module.share.shareRoutes
import su.nnedition.kord.api.module.track.trackRoutes
import su.nnedition.kord.api.module.user.playback.userPlaybackRoutes
import su.nnedition.kord.api.module.user.profile.userProfileRoutes

internal val authDispatcher = Dispatchers.IO.limitedParallelism(4)
internal val scanDispatcher = Dispatchers.IO.limitedParallelism(2)
internal const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
internal const val MAX_DATABASE_IMPORT_BYTES = 256L * 1024 * 1024
internal const val MAX_DATABASE_IMPORT_EXPANDED_BYTES = 1024L * 1024 * 1024

/** Minimal new API module used while the application is moving to a standalone Ktor process. */
fun Application.configureApiRouting(
    authService: LocalAuthService? = null,
    libraryScanService: StandaloneLibraryScanService? = null,
    playlistService: StandalonePlaylistService? = null,
    favoriteService: StandaloneFavoriteService? = null,
    mediaFolderService: MediaFolderService? = null,
    mediaCatalogService: KtorCoreMediaCatalogService? = null,
    playQueueService: StandalonePlayQueueService? = null,
    bookmarkService: StandaloneBookmarkService? = null,
    ratingService: StandaloneRatingService? = null,
    scrobbleService: StandaloneScrobbleService? = null,
    lyricsService: LyricsService? = null,
    randomSearchService: SearchService? = null,
    genreSeparators: String = ";",
    topSongsService: LastFmService? = null,
    mediaFileService: MediaFileService? = null,
    artistDao: ArtistDao? = null,
    mediaFileDao: MediaFileDao? = null,
    captionService: CaptionService? = null,
    shareService: StandaloneShareService? = null,
    avatarService: StandaloneAvatarService? = null,
    healthContributors: HealthContributorRegistry? = null,
    healthStatusAggregator: StatusAggregator? = null,
    pathWatcherService: PathWatcherService? = null,
    taskSchedulingService: TaskSchedulingService? = null,
    cacheManager: CacheManager? = null,
    meterRegistry: MeterRegistry? = null,
    statusService: StatusService? = null,
    securityService: SecurityService? = null,
    serverInfoService: ServerInfoService? = null,
    settingsService: SettingsService? = null,
    generalSettingsService: GeneralSettingsAdministrationService? = null,
    transcodingService: TranscodingService? = null,
    upnpService: UPnPService? = null,
    musicFolderAdministrationService: MusicFolderAdministrationService? = null,
    playerService: PlayerService? = null,
    jukeboxService: JukeboxService? = null,
    databaseService: DatabaseService? = null,
    videoPlaybackService: StandaloneVideoPlaybackService? = null,
    standaloneJukeboxService: su.nnedition.kord.service.jukebox.JukeboxService? = null,
    trackNoteService: StandaloneTrackNoteService? = null,
    passwordRecoveryService: StandalonePasswordRecoveryService? = null,
    tagEditService: TagEditService? = null,
    artworkSearchService: StandaloneArtworkSearchService? = null,
    artworkManagementService: ArtworkManagementService? = null,
    videoBookmarkService: StandaloneVideoBookmarkService? = null,
) {
    val audioTranscoding = transcodingService?.let(::StandaloneAudioTranscodingService)
    val loginRateLimiter = LoginRateLimiter()
    val recoveryRateLimiter = LoginRateLimiter(maxFailures = 5)
    val scanScope = libraryScanService?.let { CoroutineScope(SupervisorJob() + scanDispatcher) }
    if (scanScope != null) monitor.subscribe(ApplicationStopped) { scanScope.cancel() }
    if (passwordRecoveryService != null) monitor.subscribe(ApplicationStopped) { passwordRecoveryService.close() }
    install(Authentication) {
        bearer("server-session") {
            authenticate { credentials ->
                authService?.let { withContext(authDispatcher) { it.authenticate(credentials.token) } }
            }
        }
    }
    val api = ApiContext(
        application = this,
        authService = authService,
        libraryScanService = libraryScanService,
        playlistService = playlistService,
        favoriteService = favoriteService,
        mediaFolderService = mediaFolderService,
        mediaCatalogService = mediaCatalogService,
        playQueueService = playQueueService,
        bookmarkService = bookmarkService,
        ratingService = ratingService,
        scrobbleService = scrobbleService,
        lyricsService = lyricsService,
        randomSearchService = randomSearchService,
        genreSeparators = genreSeparators,
        topSongsService = topSongsService,
        mediaFileService = mediaFileService,
        artistDao = artistDao,
        mediaFileDao = mediaFileDao,
        captionService = captionService,
        shareService = shareService,
        avatarService = avatarService,
        healthContributors = healthContributors,
        healthStatusAggregator = healthStatusAggregator,
        pathWatcherService = pathWatcherService,
        taskSchedulingService = taskSchedulingService,
        cacheManager = cacheManager,
        meterRegistry = meterRegistry,
        statusService = statusService,
        securityService = securityService,
        serverInfoService = serverInfoService,
        settingsService = settingsService,
        generalSettingsService = generalSettingsService,
        transcodingService = transcodingService,
        upnpService = upnpService,
        musicFolderAdministrationService = musicFolderAdministrationService,
        playerService = playerService,
        jukeboxService = jukeboxService,
        databaseService = databaseService,
        videoPlaybackService = videoPlaybackService,
        standaloneJukeboxService = standaloneJukeboxService,
        trackNoteService = trackNoteService,
        passwordRecoveryService = passwordRecoveryService,
        tagEditService = tagEditService,
        artworkSearchService = artworkSearchService,
        artworkManagementService = artworkManagementService,
        videoBookmarkService = videoBookmarkService,
        audioTranscoding = audioTranscoding,
        loginRateLimiter = loginRateLimiter,
        recoveryRateLimiter = recoveryRateLimiter,
        scanScope = scanScope,
    )
    routing {
        publicRoutes(api)
        if (authService != null) {
            route("/api/v1") {
                authenticate("server-session") {
                    artworkRoutes(api)
                    searchRoutes(api)
                    albumRoutes(api)
                    artistRoutes(api)
                    catalogRoutes(api)
                    trackRoutes(api)
                    metadataRoutes(api)
                    mediaRoutes(api)
                }
                authRoutes(api)
                authenticate("server-session") {
                    shareRoutes(api)
                    playlistRoutes(api)
                    userProfileRoutes(api)
                    userPlaybackRoutes(api)
                    favoriteRoutes(api)
                    adminStatusRoutes(api)
                    adminSettingsRoutes(api)
                    adminPlayerRoutes(api)
                    databaseRoutes(api)
                    adminUserRoutes(api)
                    adminUserManagementRoutes(api)
                    adminUserLibrariesRoutes(api)
                    adminLibraryRoutes(api)
                    libraryScanRoutes(api)
                }
            }
        }
    }
}
