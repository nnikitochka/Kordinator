package su.nnedition.kord.service.media

import su.nnedition.kord.dao.AlbumDao
import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.service.CoverArtService
import su.nnedition.kord.service.search.IndexManager
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool

/** Persists the scan aggregates after directory traversal completes. */
internal class MediaLibraryScanPersistence(
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val mediaFileDao: MediaFileDao,
    private val indexManager: IndexManager,
    private val coverArtService: CoverArtService
) {
    fun persist(state: MediaLibraryScanState, foldersById: Map<Int, MusicFolder>, pool: ForkJoinPool) {
        val statistics = state.statistics
        val scanDate = statistics.getScanDate()!!
        log.info("Persisting albums")
        val albumPersistence = CompletableFuture.allOf(*state.albums.values.distinct().map { album ->
            CompletableFuture.supplyAsync({
                albumDao.createOrUpdateAlbum(album)
                indexManager.index(album)
                album
            }, pool).thenAcceptAsync({ coverArtService.persistIfNeeded(it) }, pool)
        }.toTypedArray()).thenRunAsync({
            log.info("Marking non-present albums.")
            albumDao.markNonPresent(scanDate)
        }, pool).thenRunAsync({ log.info("Album persistence complete") }, pool)

        log.info("Persisting artists")
        val artistPersistence = CompletableFuture.allOf(*state.artists.values.map { artist ->
            CompletableFuture.supplyAsync({
                artistDao.createOrUpdateArtist(artist)
                indexManager.index(artist, foldersById[artist.folderId!!]!!)
                artist
            }, pool).thenAcceptAsync({ coverArtService.persistIfNeeded(it) }, pool)
        }.toTypedArray()).thenRunAsync({
            log.info("Marking non-present artists.")
            artistDao.markNonPresent(scanDate)
        }, pool).thenRunAsync({ log.info("Artist persistence complete") }, pool)

        log.info("Marking present files")
        val filePersistence = CompletableFuture.runAsync({
            mediaFileDao.markPresent(state.encountered, scanDate)
        }, pool).thenRunAsync({
            log.info("Marking non-present files.")
            mediaFileDao.markNonPresent(scanDate)
        }, pool).thenRunAsync({ log.info("File marking complete") }, pool)

        log.info("Persisting genres")
        val genrePersistence = CompletableFuture.runAsync({
            log.info("Updating genres")
            val successful = mediaFileDao.updateGenres(state.genres.getGenres())
            log.info("Genre persistence successfully complete: {}", successful)
        }, pool)

        CompletableFuture.allOf(albumPersistence, artistPersistence, filePersistence, genrePersistence).join()
    }

    companion object {
        private val log = LoggerFactory.getLogger(MediaLibraryScanPersistence::class.java)
    }
}
