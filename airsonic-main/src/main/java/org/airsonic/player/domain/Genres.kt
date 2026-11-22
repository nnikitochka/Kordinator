package org.airsonic.player.domain

import org.apache.commons.lang3.StringUtils
import java.util.concurrent.ConcurrentHashMap

/** Represents a list of genres. */
class Genres {
    private val genres = ConcurrentHashMap<String, Genre>()

    // Preserve the original tag as well as each trimmed component from a multi-genre tag.
    fun incrementAlbumCount(genreName: String, separators: String) {
        increment(genreName, separators, album = true)
    }

    fun incrementSongCount(genreName: String, separators: String) {
        increment(genreName, separators, album = false)
    }

    fun getGenres(): List<Genre> = ArrayList(genres.values)

    private fun increment(genreName: String, separators: String, album: Boolean) {
        val splitGenres = StringUtils.split(genreName, separators)
        if (splitGenres.size > 1) {
            splitGenres.forEach { splitGenre ->
                val name = StringUtils.trim(splitGenre)
                if (StringUtils.isNotBlank(name)) {
                    increment(name, album)
                }
            }
        }
        increment(genreName, album)
    }

    private fun increment(name: String, album: Boolean) {
        val genre = genres.computeIfAbsent(name) { Genre(it) }
        if (album) genre.incrementAlbumCount() else genre.incrementSongCount()
    }
}
