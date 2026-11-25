package su.nnedition.kord.runtime

import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.dao.BookmarkDao
import su.nnedition.kord.dao.DatabaseDao
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.dao.RatingDao
import su.nnedition.kord.dao.ShareDao
import su.nnedition.kord.service.AudioScrobblerService
import su.nnedition.kord.service.BookmarkService
import su.nnedition.kord.service.CaptionService
import su.nnedition.kord.service.DatabaseService
import su.nnedition.kord.service.LastFmService
import su.nnedition.kord.service.LyricsService
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.RatingService
import su.nnedition.kord.service.SecurityService
import su.nnedition.kord.service.ServerInfoService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.ShareService
import su.nnedition.kord.service.StatusService
import su.nnedition.kord.service.TaskSchedulingService

/** Constructors for application services outside the persistence, media and playback factories. */
object RuntimeApplicationServiceFactory {
    @JvmStatic
    fun createServerInfoService(versionService: su.nnedition.kord.service.VersionService,
                                settingsService: SettingsService) = ServerInfoService(versionService, settingsService)

    @JvmStatic
    fun createStatusService(mediaFileService: MediaFileService, mediaFolderService: MediaFolderService,
                            settingsService: SettingsService, taskSchedulingService: TaskSchedulingService) =
        StatusService(mediaFileService, mediaFolderService, settingsService, taskSchedulingService)

    @JvmStatic
    fun createLyricsService() = LyricsService()

    @JvmStatic
    fun createBookmarkService(bookmarkDao: BookmarkDao, mediaFileService: MediaFileService) =
        BookmarkService(bookmarkDao, mediaFileService)

    @JvmStatic
    fun createRatingService(ratingDao: RatingDao, securityService: SecurityService,
                            mediaFileService: MediaFileService) = RatingService(ratingDao, securityService, mediaFileService)

    @JvmStatic
    fun createMusicIndexService(settingsService: SettingsService, mediaFileService: MediaFileService) =
        su.nnedition.kord.service.MusicIndexService(settingsService, mediaFileService)

    @JvmStatic
    fun createDatabaseService(settingsService: SettingsService, databaseDao: DatabaseDao,
                              taskSchedulingService: TaskSchedulingService) =
        DatabaseService(settingsService, databaseDao, taskSchedulingService)

    @JvmStatic
    fun createLastFmService(mediaFileDao: MediaFileDao, mediaFileService: MediaFileService,
                            artistDao: ArtistDao) = LastFmService(mediaFileDao, mediaFileService, artistDao)

    @JvmStatic
    fun createShareService(shareDao: ShareDao, mediaFileService: MediaFileService) =
        ShareService(shareDao, mediaFileService)

    @JvmStatic
    fun createAudioScrobblerService(settingsService: SettingsService, securityService: SecurityService) =
        AudioScrobblerService(settingsService, securityService)

    @JvmStatic
    fun createCaptionService(mediaFileService: MediaFileService, mediaFolderService: MediaFolderService,
                             settingsService: SettingsService,
                             parserFactory: su.nnedition.kord.service.metadata.MetaDataParserFactory) =
        CaptionService(mediaFileService, mediaFolderService, settingsService, parserFactory)

}
