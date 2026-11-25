package su.nnedition.kord.service.media

import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.dao.AlbumDao
import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.service.CoverArtService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.search.IndexManager
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Recursively visits files and updates scan statistics and indexes. */
internal class MediaLibraryScanWorker(
    private val mediaFileService: MediaFileService,
    private val indexManager: IndexManager,
    private val settingsService: SettingsService,
    coverArtService: CoverArtService,
    albumDao: AlbumDao,
    artistDao: ArtistDao,
    private val scanCount: AtomicInteger
) {
    private val entities = MediaLibraryScanEntities(settingsService, mediaFileService, coverArtService, albumDao, artistDao)

    fun scan(file: MediaFile, musicFolder: MusicFolder, state: MediaLibraryScanState) {
        if (scanCount.incrementAndGet() % 250 == 0) {
            log.info("Scanned media library with {} entries.", scanCount.get())
        }
        log.trace("Scanning file {} in folder {} ({})", file.path, musicFolder.id, musicFolder.name)
        if (!musicFolder.id!!.equals(file.folderId)) {
            file.folderId = musicFolder.id
            mediaFileService.updateMediaFile(file)
        }

        indexManager.index(file, musicFolder)
        if (file.isDirectory) {
            mediaFileService.getChildrenOf(file, true, true, false, false).parallelStream()
                .forEach { child -> scan(child, musicFolder, state) }
        } else {
            if (musicFolder.type == MusicFolder.Type.MEDIA) entities.updateAlbumAndArtist(file, musicFolder, state)
            state.statistics.incrementSongs(1)
        }
        entities.updateGenres(file, state.genres)
        state.encountered.computeIfAbsent(file.folderId!!) { ConcurrentHashMap.newKeySet() }.add(file.path!!)
        file.duration?.let(state.statistics::incrementTotalDurationInSeconds)
        file.fileSize?.let(state.statistics::incrementTotalLengthInBytes)
    }

    companion object {
        private val log = LoggerFactory.getLogger(MediaLibraryScanWorker::class.java)
    }
}
