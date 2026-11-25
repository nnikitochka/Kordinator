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

/** Lazily assembles application components under the shared runtime lock. */
internal class RuntimeApplicationComposition(private val context: RuntimeCompositionContext) : RuntimeApplicationComponents {
    private var versionService: VersionService? = null
    private var serverInfoService: ServerInfoService? = null
    private var statusService: StatusService? = null
    private var lyricsService: LyricsService? = null
    private var bookmarkService: BookmarkService? = null
    private var ratingService: RatingService? = null
    private var musicIndexService: MusicIndexService? = null
    private var databaseService: DatabaseService? = null
    private var lastFmService: LastFmService? = null
    private var shareService: ShareService? = null
    private var audioScrobblerService: AudioScrobblerService? = null
    private var captionService: CaptionService? = null

    @Throws(java.io.IOException::class)
    override fun createVersionService(): VersionService = synchronized(context.lock) {
        if (versionService == null) {
            versionService = VersionService()
        }
        return@synchronized versionService!!
    }

    override fun createServerInfoService(
        versions: VersionService,
        settingsService: SettingsService,
    ): ServerInfoService = synchronized(context.lock) {
        if (serverInfoService == null) {
            serverInfoService = RuntimeApplicationServiceFactory.createServerInfoService(versions, settingsService)
        }
        return@synchronized serverInfoService!!
    }

    override fun createStatusService(
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        settingsService: SettingsService,
        taskSchedulingService: TaskSchedulingService,
    ): StatusService = synchronized(context.lock) {
        if (statusService == null) {
            statusService = RuntimeApplicationServiceFactory.createStatusService(
                    mediaFileService, mediaFolderService, settingsService, taskSchedulingService)
            statusService!!.cleanup()
        }
        return@synchronized statusService!!
    }

    override fun createLyricsService(): LyricsService = synchronized(context.lock) {
        if (lyricsService == null) {
            lyricsService = RuntimeApplicationServiceFactory.createLyricsService()
        }
        return@synchronized lyricsService!!
    }

    override fun createBookmarkService(
        bookmarkDao: BookmarkDao,
        mediaFileService: MediaFileService,
    ): BookmarkService = synchronized(context.lock) {
        if (bookmarkService == null) {
            bookmarkService = RuntimeApplicationServiceFactory.createBookmarkService(bookmarkDao, mediaFileService)
        }
        return@synchronized bookmarkService!!
    }

    override fun createRatingService(
        ratingDao: RatingDao,
        securityService: SecurityService,
        mediaFileService: MediaFileService,
    ): RatingService = synchronized(context.lock) {
        if (ratingService == null) {
            ratingService = RuntimeApplicationServiceFactory.createRatingService(ratingDao, securityService, mediaFileService)
        }
        return@synchronized ratingService!!
    }

    override fun createMusicIndexService(
        settingsService: SettingsService,
        mediaFileService: MediaFileService,
    ): MusicIndexService = synchronized(context.lock) {
        if (musicIndexService == null) {
            musicIndexService = RuntimeApplicationServiceFactory.createMusicIndexService(settingsService, mediaFileService)
        }
        return@synchronized musicIndexService!!
    }

    override fun createDatabaseService(
        settingsService: SettingsService,
        databaseDao: DatabaseDao,
        taskSchedulingService: TaskSchedulingService,
    ): DatabaseService = synchronized(context.lock) {
        if (databaseService == null) {
            databaseService = RuntimeApplicationServiceFactory.createDatabaseService(
                    settingsService, databaseDao, taskSchedulingService)
            databaseService!!.init()
        }
        return@synchronized databaseService!!
    }

    override fun createLastFmService(
        mediaFileDao: MediaFileDao,
        mediaFileService: MediaFileService,
        artistDao: ArtistDao,
    ): LastFmService = synchronized(context.lock) {
        if (lastFmService == null) {
            lastFmService = RuntimeApplicationServiceFactory.createLastFmService(mediaFileDao, mediaFileService, artistDao)
            lastFmService!!.init()
        }
        return@synchronized lastFmService!!
    }

    override fun createShareService(
        shareDao: ShareDao,
        mediaFileService: MediaFileService,
    ): ShareService = synchronized(context.lock) {
        if (shareService == null) {
            shareService = RuntimeApplicationServiceFactory.createShareService(shareDao, mediaFileService)
        }
        return@synchronized shareService!!
    }

    override fun createAudioScrobblerService(
        settingsService: SettingsService,
        securityService: SecurityService,
    ): AudioScrobblerService = synchronized(context.lock) {
        if (audioScrobblerService == null) {
            audioScrobblerService = RuntimeApplicationServiceFactory.createAudioScrobblerService(
                    settingsService, securityService)
        }
        return@synchronized audioScrobblerService!!
    }

    override fun createCaptionService(
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        settingsService: SettingsService,
        metaDataParserFactory: MetaDataParserFactory,
    ): CaptionService = synchronized(context.lock) {
        if (captionService == null) {
            captionService = RuntimeApplicationServiceFactory.createCaptionService(
                    mediaFileService, mediaFolderService, settingsService, metaDataParserFactory)
        }
        return@synchronized captionService!!
    }
}
