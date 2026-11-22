package org.airsonic.player.domain

import com.google.common.util.concurrent.AtomicDouble
import java.time.Instant
import java.util.Objects
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Contains media library statistics, including the number of artists, albums and songs. */
class MediaLibraryStatistics {
    @Volatile
    private var scanDate: Instant? = Instant.now()
    private val artistCount = AtomicInteger(0)
    private val albumCount = AtomicInteger(0)
    private val songCount = AtomicInteger(0)
    private val totalLengthInBytes = AtomicLong(0)
    private val totalDurationInSeconds = AtomicDouble(0.0)

    fun setScanDate(scanDate: Instant?) {
        this.scanDate = scanDate
    }

    fun setArtistCount(artistCount: Int) {
        this.artistCount.set(artistCount)
    }

    fun setAlbumCount(albumCount: Int) {
        this.albumCount.set(albumCount)
    }

    fun setSongCount(songCount: Int) {
        this.songCount.set(songCount)
    }

    fun setTotalLengthInBytes(totalLengthInBytes: Long) {
        this.totalLengthInBytes.set(totalLengthInBytes)
    }

    fun setTotalDurationInSeconds(totalDurationInSeconds: Double) {
        this.totalDurationInSeconds.set(totalDurationInSeconds)
    }

    fun reset() {
        artistCount.set(0)
        albumCount.set(0)
        songCount.set(0)
        totalLengthInBytes.set(0)
        totalDurationInSeconds.set(0.0)
        scanDate = Instant.now()
    }

    fun incrementArtists(n: Int) {
        artistCount.addAndGet(n)
    }

    fun incrementAlbums(n: Int) {
        albumCount.addAndGet(n)
    }

    fun incrementSongs(n: Int) {
        songCount.addAndGet(n)
    }

    fun incrementTotalLengthInBytes(n: Long) {
        totalLengthInBytes.addAndGet(n)
    }

    fun incrementTotalDurationInSeconds(n: Double) {
        totalDurationInSeconds.addAndGet(n)
    }

    fun getArtistCount(): Int = artistCount.get()

    fun getAlbumCount(): Int = albumCount.get()

    fun getSongCount(): Int = songCount.get()

    fun getTotalLengthInBytes(): Long = totalLengthInBytes.get()

    fun getTotalDurationInSeconds(): Double = totalDurationInSeconds.get()

    fun getScanDate(): Instant? = scanDate

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as MediaLibraryStatistics
        return Objects.equals(artistCount.get(), other.artistCount.get()) &&
            Objects.equals(albumCount.get(), other.albumCount.get()) &&
            Objects.equals(songCount.get(), other.songCount.get()) &&
            Objects.equals(totalLengthInBytes.get(), other.totalLengthInBytes.get()) &&
            Objects.equals(totalDurationInSeconds.get(), other.totalDurationInSeconds.get()) &&
            Objects.equals(scanDate, other.scanDate)
    }

    override fun hashCode(): Int = Objects.hash(
        artistCount.get(), albumCount.get(), songCount.get(),
        totalLengthInBytes.get(), totalDurationInSeconds.get(), scanDate
    )
}
