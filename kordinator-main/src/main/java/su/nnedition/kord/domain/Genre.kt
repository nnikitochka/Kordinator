package su.nnedition.kord.domain

import java.util.concurrent.atomic.AtomicInteger

/**
 * Represents a musical genre.
 */
class Genre {
    val name: String
    private val songCount = AtomicInteger(0)
    private val albumCount = AtomicInteger(0)

    constructor(name: String) {
        this.name = name
    }

    constructor(name: String, songCount: Int, albumCount: Int) {
        this.name = name
        this.songCount.set(songCount)
        this.albumCount.set(albumCount)
    }

    fun getSongCount(): Int {
        return songCount.get()
    }

    fun getAlbumCount(): Int {
        return albumCount.get()
    }

    fun incrementAlbumCount() {
        albumCount.incrementAndGet()
    }

    fun incrementSongCount() {
        songCount.incrementAndGet()
    }
}