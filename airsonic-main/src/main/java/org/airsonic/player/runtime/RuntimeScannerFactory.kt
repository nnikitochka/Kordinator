package org.airsonic.player.runtime

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.MediaScannerService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.TaskSchedulingService
import org.airsonic.player.service.search.IndexManager

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
