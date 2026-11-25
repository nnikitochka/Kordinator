package su.nnedition.kord.api

import io.ktor.server.application.Application
import io.micrometer.core.instrument.MeterRegistry
import javax.cache.CacheManager
import kotlinx.coroutines.CoroutineScope
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

class ApiContext(
    val application: Application,
    val authService: LocalAuthService?,
    val libraryScanService: StandaloneLibraryScanService?,
    val playlistService: StandalonePlaylistService?,
    val favoriteService: StandaloneFavoriteService?,
    val mediaFolderService: MediaFolderService?,
    val mediaCatalogService: KtorCoreMediaCatalogService?,
    val playQueueService: StandalonePlayQueueService?,
    val bookmarkService: StandaloneBookmarkService?,
    val ratingService: StandaloneRatingService?,
    val scrobbleService: StandaloneScrobbleService?,
    val lyricsService: LyricsService?,
    val randomSearchService: SearchService?,
    val genreSeparators: String,
    val topSongsService: LastFmService?,
    val mediaFileService: MediaFileService?,
    val artistDao: ArtistDao?,
    val mediaFileDao: MediaFileDao?,
    val captionService: CaptionService?,
    val shareService: StandaloneShareService?,
    val avatarService: StandaloneAvatarService?,
    val healthContributors: HealthContributorRegistry?,
    val healthStatusAggregator: StatusAggregator?,
    val pathWatcherService: PathWatcherService?,
    val taskSchedulingService: TaskSchedulingService?,
    val cacheManager: CacheManager?,
    val meterRegistry: MeterRegistry?,
    val statusService: StatusService?,
    val securityService: SecurityService?,
    val serverInfoService: ServerInfoService?,
    val settingsService: SettingsService?,
    val generalSettingsService: GeneralSettingsAdministrationService?,
    val transcodingService: TranscodingService?,
    val upnpService: UPnPService?,
    val musicFolderAdministrationService: MusicFolderAdministrationService?,
    val playerService: PlayerService?,
    val jukeboxService: JukeboxService?,
    val databaseService: DatabaseService?,
    val videoPlaybackService: StandaloneVideoPlaybackService?,
    val standaloneJukeboxService: su.nnedition.kord.service.jukebox.JukeboxService?,
    val trackNoteService: StandaloneTrackNoteService?,
    val passwordRecoveryService: StandalonePasswordRecoveryService?,
    val tagEditService: TagEditService?,
    val artworkSearchService: StandaloneArtworkSearchService?,
    val artworkManagementService: ArtworkManagementService?,
    val videoBookmarkService: StandaloneVideoBookmarkService?,
    val audioTranscoding: StandaloneAudioTranscodingService?,
    val loginRateLimiter: LoginRateLimiter,
    val recoveryRateLimiter: LoginRateLimiter,
    val scanScope: CoroutineScope?,
)
