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
