package su.nnedition.kord.runtime

import su.nnedition.kord.dao.AlbumDao
import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.dao.CoverArtDao
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.service.CoverArtImageService
import su.nnedition.kord.service.CoverArtService
import su.nnedition.kord.service.DownloadService
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.PlaylistService
import su.nnedition.kord.service.SecurityService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.StatusService
import su.nnedition.kord.service.TagEditService
import su.nnedition.kord.service.TranscodingService
import su.nnedition.kord.service.metadata.DefaultMetaDataParser
import su.nnedition.kord.service.metadata.FFmpegParser
import su.nnedition.kord.service.metadata.JaudiotaggerParser
import su.nnedition.kord.service.metadata.MetaDataParserFactory

/** Lazily assembles media components under the shared runtime lock. */
internal class RuntimeMediaComposition(private val context: RuntimeCompositionContext) : RuntimeMediaComponents {
    private var mediaFolderService: MediaFolderService? = null
    private var mediaFileService: MediaFileService? = null
    private var defaultMetaDataParser: DefaultMetaDataParser? = null
    private var ffmpegParser: FFmpegParser? = null
    private var jaudiotaggerParser: JaudiotaggerParser? = null
    private var metaDataParserFactory: MetaDataParserFactory? = null
    private var coverArtService: CoverArtService? = null
    private var tagEditService: TagEditService? = null
    private var downloadService: DownloadService? = null
    private var coverArtImageService: CoverArtImageService? = null

    override fun createMediaFolderService(): MediaFolderService = synchronized(context.lock) {
        if (context.persistenceGraph == null) {
            throw IllegalStateException("Persistence graph must be created before media services")
        }
        if (mediaFolderService == null) {
            mediaFolderService = RuntimeMediaServiceFactory.createMediaFolderService(
                    context.persistenceGraph!!.daos.musicFolderDao,
                    context.persistenceGraph!!.daos.mediaFileDao, context.root.createCacheManager())
        }
        return@synchronized mediaFolderService!!
    }

    override fun createMediaFileService(
        securityService: SecurityService,
        settingsService: SettingsService,
        mediaFileDao: MediaFileDao,
        albumDao: AlbumDao,
        parser: JaudiotaggerParser,
        metaDataParserFactory: MetaDataParserFactory,
        coverArtService: CoverArtService,
    ): MediaFileService = synchronized(context.lock) {
        if (mediaFileService == null) {
            mediaFileService = RuntimeMediaServiceFactory.createMediaFileService(
                    securityService, settingsService, mediaFileDao, albumDao, createMediaFolderService(),
                    parser, metaDataParserFactory, coverArtService, context.root.createCacheManager())
        }
        return@synchronized mediaFileService!!
    }

    override fun createDefaultMetaDataParser(): DefaultMetaDataParser = synchronized(context.lock) {
        if (defaultMetaDataParser == null) {
            defaultMetaDataParser = RuntimeMediaServiceFactory.createDefaultMetaDataParser(createMediaFolderService())
        }
        return@synchronized defaultMetaDataParser!!
    }

    override fun createFFmpegParser(): FFmpegParser = synchronized(context.lock) {
        if (ffmpegParser == null) {
            ffmpegParser = RuntimeMediaServiceFactory.createFFmpegParser(createMediaFolderService())
        }
        return@synchronized ffmpegParser!!
    }

    override fun createJaudiotaggerParser(): JaudiotaggerParser = synchronized(context.lock) {
        if (jaudiotaggerParser == null) {
            jaudiotaggerParser = RuntimeMediaServiceFactory.createJaudiotaggerParser(createMediaFolderService())
        }
        return@synchronized jaudiotaggerParser!!
    }

    override fun createMetaDataParserFactory(): MetaDataParserFactory = synchronized(context.lock) {
        if (metaDataParserFactory == null) {
            metaDataParserFactory = RuntimeMediaServiceFactory.createMetaDataParserFactory(
                    createJaudiotaggerParser(), createFFmpegParser(), createDefaultMetaDataParser())
        }
        return@synchronized metaDataParserFactory!!
    }

    override fun createCoverArtService(
        coverArtDao: CoverArtDao,
    ): CoverArtService = synchronized(context.lock) {
        if (coverArtService == null) {
            coverArtService = RuntimeMediaServiceFactory.createCoverArtService(
                    coverArtDao, createMediaFolderService(), createJaudiotaggerParser(), context.root.createCacheManager())
        }
        return@synchronized coverArtService!!
    }

    override fun createTagEditService(
        metaDataParserFactory: MetaDataParserFactory,
        mediaFileService: MediaFileService,
        securityService: SecurityService,
    ): TagEditService = synchronized(context.lock) {
        if (tagEditService == null) {
            tagEditService = RuntimeMediaServiceFactory.createTagEditService(
                    metaDataParserFactory, mediaFileService, createMediaFolderService(), securityService)
        }
        return@synchronized tagEditService!!
    }

    override fun createDownloadService(
        statusService: StatusService,
        securityService: SecurityService,
        playlistService: PlaylistService,
        settingsService: SettingsService,
        mediaFileService: MediaFileService,
        coverArtService: CoverArtService,
    ): DownloadService = synchronized(context.lock) {
        if (downloadService == null) {
            downloadService = RuntimeMediaServiceFactory.createDownloadService(statusService, securityService,
                    playlistService, settingsService, mediaFileService, createMediaFolderService(), coverArtService)
        }
        return@synchronized downloadService!!
    }

    override fun createCoverArtImageService(
        mediaFileService: MediaFileService,
        coverArtService: CoverArtService,
        transcodingService: TranscodingService,
        settingsService: SettingsService,
        playlistService: PlaylistService,
        artistDao: ArtistDao,
        albumDao: AlbumDao,
    ): CoverArtImageService = synchronized(context.lock) {
        if (coverArtImageService == null) {
            coverArtImageService = RuntimeMediaServiceFactory.createCoverArtImageService(
                    mediaFileService, coverArtService, transcodingService, settingsService, playlistService,
                    artistDao, albumDao, createJaudiotaggerParser())
            coverArtImageService!!.init()
        }
        return@synchronized coverArtImageService!!
    }
}
