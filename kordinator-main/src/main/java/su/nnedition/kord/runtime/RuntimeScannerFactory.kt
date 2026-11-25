package su.nnedition.kord.runtime

import su.nnedition.kord.dao.AlbumDao
import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.service.CoverArtService
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.media.MediaScannerService
import su.nnedition.kord.service.PlaylistService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.TaskSchedulingService
import su.nnedition.kord.service.search.IndexManager

/** Constructs the media library scanner from its explicit runtime dependencies. */
object RuntimeScannerFactory {
    @JvmStatic
    fun createMediaScannerService(
        playlistService: PlaylistService,
        settingsService: SettingsService,
        indexManager: IndexManager,
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        coverArtService: CoverArtService,
        mediaFileDao: MediaFileDao,
        artistDao: ArtistDao,
        albumDao: AlbumDao,
        taskSchedulingService: TaskSchedulingService,
        scannerParallelism: Int,
    ) = MediaScannerService(
        playlistService, settingsService, indexManager, mediaFileService, mediaFolderService,
        coverArtService, mediaFileDao, artistDao, albumDao, taskSchedulingService, scannerParallelism,
    )
}
