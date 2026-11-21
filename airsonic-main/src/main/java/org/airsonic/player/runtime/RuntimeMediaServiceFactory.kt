package org.airsonic.player.runtime

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.CoverArtDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.dao.MusicFolderDao
import org.airsonic.player.i18n.LocaleService
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.CoverArtImageService
import org.airsonic.player.service.DownloadService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.MediaScannerService
import org.airsonic.player.service.MusicIndexStateService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.StatusService
import org.airsonic.player.service.TagEditService
import org.airsonic.player.service.TranscodingService
import org.airsonic.player.service.UserAdministrationService
import org.airsonic.player.service.JWTSecurityService
import org.airsonic.player.service.hls.HlsPlaylistService
import org.airsonic.player.service.hls.HlsSegmentService
import org.airsonic.player.service.metadata.DefaultMetaDataParser
import org.airsonic.player.service.metadata.FFmpegParser
import org.airsonic.player.service.metadata.JaudiotaggerParser
import org.airsonic.player.service.metadata.MetaDataParserFactory
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
        localeService: LocaleService,
        cacheManager: CacheManager,
    ): MediaFileService = MediaFileService(
        securityService, settingsService, mediaFileDao, mediaFolderService, albumDao,
        parser, metaDataParserFactory, coverArtService, localeService, cacheManager,
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
    fun createHlsPlaylistService(jwtSecurityService: JWTSecurityService): HlsPlaylistService =
        HlsPlaylistService(jwtSecurityService)

    @JvmStatic
    fun createHlsSegmentService(
        mediaFileService: MediaFileService,
        securityService: SecurityService,
        statusService: StatusService,
        settingsService: SettingsService,
        transcodingService: TranscodingService,
    ): HlsSegmentService = HlsSegmentService(
        mediaFileService, securityService, statusService, settingsService, transcodingService,
    )

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
    fun createUserAdministrationService(
        securityService: SecurityService,
        settingsService: SettingsService,
        mediaFolderService: MediaFolderService,
    ): UserAdministrationService = UserAdministrationService(securityService, settingsService, mediaFolderService)

    @JvmStatic
    fun createMusicIndexStateService(
        scanner: MediaScannerService,
        settingsService: SettingsService,
        mediaFolderService: MediaFolderService,
    ): MusicIndexStateService = MusicIndexStateService(scanner, settingsService, mediaFolderService)

    @JvmStatic
    fun createCoverArtImageService(
        mediaFileService: MediaFileService,
        coverArtService: CoverArtService,
        transcodingService: TranscodingService,
        settingsService: SettingsService,
        playlistService: PlaylistService,
        artistDao: org.airsonic.player.dao.ArtistDao,
        albumDao: AlbumDao,
        jaudiotaggerParser: JaudiotaggerParser,
    ): CoverArtImageService = CoverArtImageService(
        mediaFileService, coverArtService, transcodingService, settingsService, playlistService,
        artistDao, albumDao, jaudiotaggerParser,
    )
}
