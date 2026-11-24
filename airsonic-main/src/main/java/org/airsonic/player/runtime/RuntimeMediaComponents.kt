package org.airsonic.player.runtime

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.CoverArtDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.CoverArtImageService
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.DownloadService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.StatusService
import org.airsonic.player.service.TagEditService
import org.airsonic.player.service.TranscodingService
import org.airsonic.player.service.metadata.DefaultMetaDataParser
import org.airsonic.player.service.metadata.FFmpegParser
import org.airsonic.player.service.metadata.JaudiotaggerParser
import org.airsonic.player.service.metadata.MetaDataParserFactory

/** Creation boundary for the media components owned by one runtime. */
interface RuntimeMediaComponents {
    fun createMediaFolderService(): MediaFolderService

    fun createMediaFileService(
        securityService: SecurityService,
        settingsService: SettingsService,
        mediaFileDao: MediaFileDao,
        albumDao: AlbumDao,
        parser: JaudiotaggerParser,
        metaDataParserFactory: MetaDataParserFactory,
        coverArtService: CoverArtService,
    ): MediaFileService

    fun createDefaultMetaDataParser(): DefaultMetaDataParser

    fun createFFmpegParser(): FFmpegParser

    fun createJaudiotaggerParser(): JaudiotaggerParser

    fun createMetaDataParserFactory(): MetaDataParserFactory

    fun createCoverArtService(
        coverArtDao: CoverArtDao,
    ): CoverArtService

    fun createTagEditService(
        metaDataParserFactory: MetaDataParserFactory,
        mediaFileService: MediaFileService,
        securityService: SecurityService,
    ): TagEditService

    fun createDownloadService(
        statusService: StatusService,
        securityService: SecurityService,
        playlistService: PlaylistService,
        settingsService: SettingsService,
        mediaFileService: MediaFileService,
        coverArtService: CoverArtService,
    ): DownloadService

    fun createCoverArtImageService(
        mediaFileService: MediaFileService,
        coverArtService: CoverArtService,
        transcodingService: TranscodingService,
        settingsService: SettingsService,
        playlistService: PlaylistService,
        artistDao: ArtistDao,
        albumDao: AlbumDao,
    ): CoverArtImageService
}
