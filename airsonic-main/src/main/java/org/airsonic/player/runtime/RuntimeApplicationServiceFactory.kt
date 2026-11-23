package org.airsonic.player.runtime

import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.BookmarkDao
import org.airsonic.player.dao.DatabaseDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.dao.RatingDao
import org.airsonic.player.dao.ShareDao
import org.airsonic.player.service.AudioScrobblerService
import org.airsonic.player.service.BookmarkService
import org.airsonic.player.service.CaptionService
import org.airsonic.player.service.DatabaseService
import org.airsonic.player.service.LastFmService
import org.airsonic.player.service.LyricsService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.RatingService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.ServerInfoService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.ShareService
import org.airsonic.player.service.StatusService
import org.airsonic.player.service.TaskSchedulingService
import org.airsonic.player.service.TranscodingService

/** Constructors for application services outside the persistence, media and playback factories. */
object RuntimeApplicationServiceFactory {
    @JvmStatic
    fun createServerInfoService(versionService: org.airsonic.player.service.VersionService,
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
        org.airsonic.player.service.MusicIndexService(settingsService, mediaFileService)

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
                             parserFactory: org.airsonic.player.service.metadata.MetaDataParserFactory) =
        CaptionService(mediaFileService, mediaFolderService, settingsService, parserFactory)

}
