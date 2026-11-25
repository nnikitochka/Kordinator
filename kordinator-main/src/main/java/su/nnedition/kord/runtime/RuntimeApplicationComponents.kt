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
import su.nnedition.kord.service.MusicIndexService
import su.nnedition.kord.service.RatingService
import su.nnedition.kord.service.SecurityService
import su.nnedition.kord.service.ServerInfoService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.ShareService
import su.nnedition.kord.service.StatusService
import su.nnedition.kord.service.TaskSchedulingService
import su.nnedition.kord.service.VersionService
import su.nnedition.kord.service.metadata.MetaDataParserFactory

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
