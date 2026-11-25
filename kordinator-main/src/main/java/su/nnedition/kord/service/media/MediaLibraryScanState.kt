package su.nnedition.kord.service.media

import su.nnedition.kord.domain.Album
import su.nnedition.kord.domain.Artist
import su.nnedition.kord.domain.Genres
import su.nnedition.kord.domain.MediaLibraryStatistics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Thread-safe values accumulated during a single library scan. */
internal class MediaLibraryScanState(val statistics: MediaLibraryStatistics) {
    val albumCounts = ConcurrentHashMap<String, AtomicInteger>()
    val artists = ConcurrentHashMap<String, Artist>()
    val albums = ConcurrentHashMap<String, Album>()
    val albumsInDatabase = ConcurrentHashMap<Int, Album>()
    val encountered = ConcurrentHashMap<Int, MutableSet<String>>()
    val genres = Genres()
}
