package su.nnedition.kord.runtime

import su.nnedition.kord.dao.AlbumDao
import su.nnedition.kord.dao.CoverArtDao
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.dao.MusicFolderDao
import su.nnedition.kord.service.CoverArtService
import su.nnedition.kord.service.CoverArtImageService
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
import javax.cache.CacheManager

/** Constructors for the media catalog and metadata parsing portion of the runtime graph. */
object RuntimeMediaServiceFactory {
    @JvmStatic
    fun createMediaFolderService(
        musicFolderDao: MusicFolderDao,
        mediaFileDao: MediaFileDao,
        cacheManager: CacheManager,
    ): MediaFolderService = MediaFolderService(musicFolderDao, mediaFileDao, cacheManager)

    @JvmStatic
    fun createMediaFileService(
        securityService: SecurityService,
        settingsService: SettingsService,
        mediaFileDao: MediaFileDao,
        albumDao: AlbumDao,
        mediaFolderService: MediaFolderService,
        parser: JaudiotaggerParser,
        metaDataParserFactory: MetaDataParserFactory,
        coverArtService: CoverArtService,
        cacheManager: CacheManager,
    ): MediaFileService = MediaFileService(
        securityService, settingsService, mediaFileDao, mediaFolderService, albumDao,
        parser, metaDataParserFactory, coverArtService, cacheManager,
    )

    @JvmStatic
    fun createDefaultMetaDataParser(mediaFolderService: MediaFolderService): DefaultMetaDataParser =
        DefaultMetaDataParser(mediaFolderService)

    @JvmStatic
    fun createFFmpegParser(mediaFolderService: MediaFolderService): FFmpegParser = FFmpegParser(mediaFolderService)

    @JvmStatic
    fun createJaudiotaggerParser(mediaFolderService: MediaFolderService): JaudiotaggerParser =
        JaudiotaggerParser(mediaFolderService)

    @JvmStatic
    fun createMetaDataParserFactory(
        jaudiotaggerParser: JaudiotaggerParser,
        ffmpegParser: FFmpegParser,
        defaultMetaDataParser: DefaultMetaDataParser,
    ): MetaDataParserFactory = MetaDataParserFactory(
        arrayListOf(jaudiotaggerParser, ffmpegParser, defaultMetaDataParser),
    )

    @JvmStatic
    fun createCoverArtService(
        coverArtDao: CoverArtDao,
        mediaFolderService: MediaFolderService,
        jaudiotaggerParser: JaudiotaggerParser,
        cacheManager: CacheManager,
    ): CoverArtService = CoverArtService(coverArtDao, mediaFolderService, jaudiotaggerParser, cacheManager)

    @JvmStatic
    fun createTagEditService(
        metaDataParserFactory: MetaDataParserFactory,
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        securityService: SecurityService,
    ): TagEditService = TagEditService(metaDataParserFactory, mediaFileService, mediaFolderService, securityService)

    @JvmStatic
    fun createDownloadService(
        statusService: StatusService,
        securityService: SecurityService,
        playlistService: PlaylistService,
        settingsService: SettingsService,
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        coverArtService: CoverArtService,
    ): DownloadService = DownloadService(
        statusService, securityService, playlistService, settingsService, mediaFileService,
        mediaFolderService, coverArtService,
    )

    @JvmStatic
    fun createCoverArtImageService(
        mediaFileService: MediaFileService,
        coverArtService: CoverArtService,
        transcodingService: TranscodingService,
        settingsService: SettingsService,
        playlistService: PlaylistService,
        artistDao: su.nnedition.kord.dao.ArtistDao,
        albumDao: AlbumDao,
        jaudiotaggerParser: JaudiotaggerParser,
    ): CoverArtImageService = CoverArtImageService(
        mediaFileService, coverArtService, transcodingService, settingsService, playlistService,
        artistDao, albumDao, jaudiotaggerParser,
    )
}
