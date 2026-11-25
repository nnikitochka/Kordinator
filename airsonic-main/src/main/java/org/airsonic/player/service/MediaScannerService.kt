package org.airsonic.player.service

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.search.IndexManager
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinWorkerThread
import java.util.concurrent.atomic.AtomicInteger

/** Coordinates scheduled and asynchronous scans of the media library. */
class MediaScannerService(
    playlistService: PlaylistService,
    settingsService: SettingsService,
    private val indexManager: IndexManager,
    mediaFileService: MediaFileService,
    private val mediaFolderService: MediaFolderService,
    private val coverArtService: CoverArtService,
    mediaFileDao: MediaFileDao,
    artistDao: ArtistDao,
    albumDao: AlbumDao,
    private val taskService: TaskSchedulingService,
    private val scannerParallelism: Int
) {
    private var settings = settingsService
    private var playlists = playlistService
    private var mediaFiles = mediaFileService
    private var mediaFilesDao = mediaFileDao
    private var artistsDao = artistDao
    private var albumsDao = albumDao
    @Volatile private var scanning = false
    private val scanCounter = AtomicInteger(0)
    val scanCount: Int get() = scanCounter.get()

    fun init() {
        indexManager.initializeIndexDirectory()
        schedule()
    }

    @Throws(IOException::class)
    fun initNoSchedule() {
        indexManager.deleteOldIndexFiles()
    }

    @Synchronized
    fun schedule() {
        val daysBetween = settings.getIndexCreationInterval()
        val hour = settings.getIndexCreationHour()
        if (daysBetween == -1) {
            log.info("Automatic media scanning disabled.")
            taskService.unscheduleTask(TASK_NAME)
            return
        }

        val now = LocalDateTime.now()
        var nextRun = now.withHour(hour).withMinute(0).withSecond(0)
        if (now.isAfter(nextRun)) nextRun = nextRun.plusDays(1)
        val initialDelayMillis = ChronoUnit.MILLIS.between(now, nextRun)
        val firstTime = Instant.now().plusMillis(initialDelayMillis)
        taskService.scheduleAtFixedRate(TASK_NAME, this::scanLibrary, firstTime, Duration.ofDays(daysBetween.toLong()), true)
        log.info("Automatic media library scanning scheduled to run every {} day(s), starting at {}", daysBetween, nextRun)

        if (neverScanned()) {
            log.info("Media library never scanned. Doing it now.")
            scanLibrary()
        }
    }

    fun neverScanned(): Boolean = indexManager.getStatistics() == null

    val isScanning: Boolean get() = scanning

    @Synchronized
    fun scanLibrary() {
        if (isScanning) return
        scanning = true
        val pool = ForkJoinPool(scannerParallelism, MEDIA_SCANNER_THREAD_FACTORY, null, true)
        CompletableFuture.runAsync({ doScanLibrary(pool) }, pool)
            .thenRunAsync({ playlists.importPlaylists() }, pool)
            .whenComplete { _, _ -> pool.shutdown() }
            .whenComplete { _, _ -> scanning = false }
    }

    private fun doScanLibrary(pool: ForkJoinPool) {
        log.info("Starting to scan media library.")
        val statistics = org.airsonic.player.domain.MediaLibraryStatistics()
        log.debug("New last scan date is {}", statistics.getScanDate())
        try {
        scanCounter.set(0)
            mediaFiles.setMemoryCacheEnabled(false)
            indexManager.startIndexing()

            val state = MediaLibraryScanState(statistics)
            val folders = mediaFolderService.getAllMusicFolders()
            val foldersById = folders.associateBy { it.id!! }
            val worker = MediaLibraryScanWorker(
                mediaFiles, indexManager, settings, coverArtService, albumsDao, artistsDao, scanCounter
            )
            folders.parallelStream().forEach { folder ->
                val root = mediaFiles.getMediaFile(java.nio.file.Paths.get(""), folder, false)!!
                worker.scan(root, folder, state)
            }

            log.info("Scanned media library with {} entries.", scanCounter.get())
            statistics.incrementArtists(state.albumCounts.size)
            statistics.incrementAlbums(state.albumCounts.values.parallelStream().mapToInt(AtomicInteger::get).sum())

            MediaLibraryScanPersistence(albumsDao, artistsDao, mediaFilesDao, indexManager, coverArtService)
                .persist(state, foldersById, pool)

            if (settings.getClearFullScanSettingAfterScan()) {
                settings.setClearFullScanSettingAfterScan(null as Boolean?)
                settings.setFullScan(null)
                settings.save()
            }
            log.info("Completed media library scan.")
        } catch (failure: Throwable) {
            log.error("Failed to scan media library.", failure)
        } finally {
            mediaFiles.setMemoryCacheEnabled(true)
            indexManager.stopIndexing(statistics)
            log.info("Media library scan took {}s", ChronoUnit.SECONDS.between(statistics.getScanDate(), Instant.now()))
        }
    }

    fun setSettingsService(settingsService: SettingsService) {
        settings = settingsService
    }

    fun setMediaFileService(mediaFileService: MediaFileService) {
        mediaFiles = mediaFileService
    }

    fun setMediaFileDao(mediaFileDao: MediaFileDao) {
        mediaFilesDao = mediaFileDao
    }

    fun setArtistDao(artistDao: ArtistDao) {
        artistsDao = artistDao
    }

    fun setAlbumDao(albumDao: AlbumDao) {
        albumsDao = albumDao
    }

    fun setPlaylistService(playlistService: PlaylistService) {
        playlists = playlistService
    }

    companion object {
        private const val TASK_NAME = "mediascanner-IndexingTask"
        private val log = LoggerFactory.getLogger(MediaScannerService::class.java)
        private val MEDIA_SCANNER_THREAD_FACTORY = ForkJoinPool.ForkJoinWorkerThreadFactory { pool ->
            ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool).apply {
                name = "MediaLibraryScanner-$poolIndex"
                priority = Thread.MIN_PRIORITY
            }
        }
    }
}
