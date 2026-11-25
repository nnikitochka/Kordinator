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
