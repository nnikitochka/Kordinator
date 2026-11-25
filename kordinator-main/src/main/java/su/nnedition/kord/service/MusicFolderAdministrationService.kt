package su.nnedition.kord.service

import su.nnedition.kord.service.media.MediaScannerService
import su.nnedition.kord.command.MusicFolderSettingsCommand
import su.nnedition.kord.command.MusicFolderSettingsCommand.MusicFolderInfo
import su.nnedition.kord.dao.AlbumDao
import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.domain.MediaLibraryStatistics
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.search.IndexManager
import org.slf4j.LoggerFactory

/** Library administration operations shared by REST clients. */
class MusicFolderAdministrationService(
    private val settingsService: SettingsService,
    private val mediaScannerService: MediaScannerService,
    private val mediaFolderService: MediaFolderService,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val mediaFileDao: MediaFileDao,
    private val indexManager: IndexManager,
    private val playlistService: PlaylistService,
    private val coverArtService: CoverArtService,
) {
    val settings: MusicFolderSettingsCommand
        get() {
            val command = MusicFolderSettingsCommand()
            command.interval = settingsService.getIndexCreationInterval().toString()
            command.hour = settingsService.getIndexCreationHour().toString()
            command.isFastCache = settingsService.isFastCacheEnabled()
            command.isOrganizeByFolderStructure = settingsService.isOrganizeByFolderStructure()
            command.isScanning = mediaScannerService.isScanning
            command.musicFolders = wrap(mediaFolderService.getAllMusicFolders(true, true))
            command.newMusicFolder = MusicFolderInfo()
            command.deletedMusicFolders = wrap(mediaFolderService.getDeletedMusicFolders())
            command.uploadsFolder = settingsService.getUploadsFolder()
            command.excludePatternString = settingsService.getExcludePatternString()
            command.ignoreSymLinks = settingsService.getIgnoreSymLinks()
            command.fullScan = settingsService.getFullScan()
            command.clearFullScanSettingAfterScan =
                if (!settingsService.getFullScan()) settingsService.getFullScan() else settingsService.getClearFullScanSettingAfterScan()
            return command
        }

    fun expunge() {
        val statistics: MediaLibraryStatistics? = indexManager.getStatistics()
        if (statistics != null) {
            LOG.debug("Cleaning search index...")
            indexManager.startIndexing()
            try {
                indexManager.expunge()
            } finally {
                indexManager.stopIndexing(statistics)
            }
            LOG.debug("Search index cleanup complete.")
        } else {
            LOG.warn("Missing index statistics - index probably hasn't been created yet. Not expunging index.")
        }

        LOG.debug("Cleaning database...")
        LOG.debug("Deleting non-present artists...")
        artistDao.expunge()
        LOG.debug("Deleting non-present albums...")
        albumDao.expunge()
        LOG.debug("Deleting non-present media files...")
        mediaFileDao.expunge()
        LOG.debug("Deleting non-present cover art...")
        coverArtService.expunge()
        LOG.debug("Deleting non-present media folders...")
        mediaFolderService.expunge()
        LOG.debug("Refreshing playlist stats...")
        playlistService.refreshPlaylistsStats()
        LOG.debug("Database cleanup complete.")
    }

    private fun wrap(musicFolders: List<MusicFolder>): List<MusicFolderInfo> =
        musicFolders.map { folder ->
            val overlaps = MediaFolderService.getMusicFolderPathOverlaps(
                folder,
                mediaFolderService.getAllMusicFolders(true, true),
            )
            MusicFolderInfo(
                folder,
                !overlaps.left.isEmpty() || !overlaps.middle.isEmpty() || !overlaps.right.isEmpty(),
                MediaFolderService.logMusicFolderOverlap(overlaps),
            )
        }.sortedWith(compareBy<MusicFolderInfo> { it.type }.thenBy { it.id })

    fun save(command: MusicFolderSettingsCommand) {
        for (musicFolderInfo in command.musicFolders!!) {
            if (musicFolderInfo.delete) {
                mediaFolderService.deleteMusicFolder(musicFolderInfo.id)
            } else {
                val musicFolder = musicFolderInfo.toMusicFolder()
                if (musicFolder != null) mediaFolderService.updateMusicFolder(musicFolder)
            }
        }
        val newMusicFolder = command.newMusicFolder!!.toMusicFolder()
        if (newMusicFolder != null) mediaFolderService.createMusicFolder(newMusicFolder)

        settingsService.setIndexCreationInterval(command.interval!!.toInt())
        settingsService.setIndexCreationHour(command.hour!!.toInt())
        settingsService.setFastCacheEnabled(command.isFastCache)
        settingsService.setOrganizeByFolderStructure(command.isOrganizeByFolderStructure)
        settingsService.setUploadsFolder(command.uploadsFolder)
        settingsService.setExcludePatternString(command.excludePatternString)
        settingsService.setIgnoreSymLinks(command.ignoreSymLinks)
        settingsService.setFullScan(command.fullScan)
        settingsService.setClearFullScanSettingAfterScan(
            if (command.fullScan != true) command.fullScan else command.clearFullScanSettingAfterScan,
        )
        settingsService.save()
        mediaScannerService.schedule()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MusicFolderAdministrationService::class.java)
    }
}
