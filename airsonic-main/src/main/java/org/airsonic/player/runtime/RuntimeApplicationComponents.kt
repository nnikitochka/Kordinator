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
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.media.MediaFolderService
import org.airsonic.player.service.MusicIndexService
import org.airsonic.player.service.RatingService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.ServerInfoService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.ShareService
import org.airsonic.player.service.StatusService
import org.airsonic.player.service.TaskSchedulingService
import org.airsonic.player.service.VersionService
import org.airsonic.player.service.metadata.MetaDataParserFactory

/** Creation boundary for the application components owned by one runtime. */
interface RuntimeApplicationComponents {
    @Throws(java.io.IOException::class)
    fun createVersionService(): VersionService

    fun createServerInfoService(
        versions: VersionService,
        settingsService: SettingsService,
    ): ServerInfoService

    fun createStatusService(
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        settingsService: SettingsService,
        taskSchedulingService: TaskSchedulingService,
    ): StatusService

    fun createLyricsService(): LyricsService

    fun createBookmarkService(
        bookmarkDao: BookmarkDao,
        mediaFileService: MediaFileService,
    ): BookmarkService

    fun createRatingService(
        ratingDao: RatingDao,
        securityService: SecurityService,
        mediaFileService: MediaFileService,
    ): RatingService

    fun createMusicIndexService(
        settingsService: SettingsService,
        mediaFileService: MediaFileService,
    ): MusicIndexService

    fun createDatabaseService(
        settingsService: SettingsService,
        databaseDao: DatabaseDao,
        taskSchedulingService: TaskSchedulingService,
    ): DatabaseService

    fun createLastFmService(
        mediaFileDao: MediaFileDao,
        mediaFileService: MediaFileService,
        artistDao: ArtistDao,
    ): LastFmService

    fun createShareService(
        shareDao: ShareDao,
        mediaFileService: MediaFileService,
    ): ShareService

    fun createAudioScrobblerService(
        settingsService: SettingsService,
        securityService: SecurityService,
    ): AudioScrobblerService

    fun createCaptionService(
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        settingsService: SettingsService,
        metaDataParserFactory: MetaDataParserFactory,
    ): CaptionService
}
