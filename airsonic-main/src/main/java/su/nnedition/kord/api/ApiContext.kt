package su.nnedition.kord.api

import io.ktor.server.application.Application
import io.micrometer.core.instrument.MeterRegistry
import javax.cache.CacheManager
import kotlinx.coroutines.CoroutineScope
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.health.HealthContributorRegistry
import org.airsonic.player.health.StatusAggregator
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.security.LoginRateLimiter
import org.airsonic.player.security.StandalonePasswordRecoveryService
import org.airsonic.player.service.ArtworkManagementService
import org.airsonic.player.service.CaptionService
import org.airsonic.player.service.DatabaseService
import org.airsonic.player.service.GeneralSettingsAdministrationService
import org.airsonic.player.service.JukeboxService
import org.airsonic.player.service.KtorCoreMediaCatalogService
import org.airsonic.player.service.LastFmService
import org.airsonic.player.service.LyricsService
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.media.MediaFolderService
import org.airsonic.player.service.MusicFolderAdministrationService
import org.airsonic.player.service.PathWatcherService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.SearchService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.ServerInfoService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.StandaloneArtworkSearchService
import org.airsonic.player.service.StandaloneAudioTranscodingService
import org.airsonic.player.service.StandaloneAvatarService
import org.airsonic.player.service.StandaloneBookmarkService
import org.airsonic.player.service.StandaloneFavoriteService
import org.airsonic.player.service.library.scan.StandaloneLibraryScanService
import org.airsonic.player.service.StandalonePlayQueueService
import org.airsonic.player.service.StandalonePlaylistService
import org.airsonic.player.service.StandaloneRatingService
import org.airsonic.player.service.StandaloneScrobbleService
import org.airsonic.player.service.StandaloneShareService
import org.airsonic.player.service.StandaloneTrackNoteService
import org.airsonic.player.service.StandaloneVideoBookmarkService
import org.airsonic.player.service.StandaloneVideoPlaybackService
import org.airsonic.player.service.StatusService
import org.airsonic.player.service.TagEditService
import org.airsonic.player.service.TaskSchedulingService
import org.airsonic.player.service.TranscodingService
import org.airsonic.player.service.UPnPService

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
    val standaloneJukeboxService: org.airsonic.player.service.jukebox.JukeboxService?,
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
