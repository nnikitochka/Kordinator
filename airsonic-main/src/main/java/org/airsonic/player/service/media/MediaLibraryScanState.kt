package org.airsonic.player.service.media

import org.airsonic.player.domain.Album
import org.airsonic.player.domain.Artist
import org.airsonic.player.domain.Genres
import org.airsonic.player.domain.MediaLibraryStatistics
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
