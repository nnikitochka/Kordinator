package org.airsonic.player.domain

import com.google.common.util.concurrent.AtomicDouble
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

open class Album {
    open var id: Int = 0
    open var path: String? = null
    open var name: String? = null
    open var artist: String? = null

    private val songCountValue = AtomicInteger()
    private val durationValue = AtomicDouble()
    private val playCountValue = AtomicInteger()

    open var songCount: Int
        get() = songCountValue.get()
        set(value) {
            songCountValue.set(value)
        }

    open var duration: Double
        get() = durationValue.get()
        set(value) {
            durationValue.set(value)
        }

    open var year: Int? = null
    open var genre: String? = null

    open var playCount: Int
        get() = playCountValue.get()
        set(value) {
            playCountValue.set(value)
        }

    open var lastPlayed: Instant? = null
    open var comment: String? = null
    open var created: Instant? = null
    open var lastScanned: Instant? = null
    open var isPresent: Boolean = false
    open var folderId: Int? = null
    open var musicBrainzReleaseId: String? = null
    open var art: CoverArt? = null

    constructor()

    constructor(
        id: Int,
        path: String?,
        name: String?,
        artist: String?,
        songCount: Int,
        duration: Double,
        year: Int?,
        genre: String?,
        playCount: Int,
        lastPlayed: Instant?,
        comment: String?,
        created: Instant?,
        lastScanned: Instant?,
        present: Boolean,
        folderId: Int?,
        musicBrainzReleaseId: String?,
    ) {
        this.id = id
        this.path = path
        this.name = name
        this.artist = artist
        this.songCount = songCount
        this.duration = duration
        this.year = year
        this.genre = genre
        this.playCount = playCount
        this.lastPlayed = lastPlayed
        this.comment = comment
        this.created = created
        this.lastScanned = lastScanned
        this.isPresent = present
        this.folderId = folderId
        this.musicBrainzReleaseId = musicBrainzReleaseId
    }

    open fun incrementSongCount() {
        songCountValue.incrementAndGet()
    }

    open fun incrementDuration(duration: Double) {
        durationValue.addAndGet(duration)
    }

    open fun incrementPlayCount() {
        playCountValue.incrementAndGet()
    }
}
