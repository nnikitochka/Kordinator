package org.airsonic.player.runtime

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.CoverArtDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.dao.MusicFolderDao
import org.airsonic.player.i18n.LocaleService
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
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
}
