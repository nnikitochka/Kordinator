package org.airsonic.player.controller.rest.genres

import org.airsonic.player.domain.Genre
import org.subsonic.restapi.Genres

fun interface GenreLookup {
    fun getGenres(): List<Genre>
}

/** Builds the API representation in the order supplied by the existing genre lookup. */
class GenresService(private val genreLookup: GenreLookup) {
    fun getGenres(): Genres = Genres().also { result ->
        genreLookup.getGenres().forEach { genre ->
            result.genre += org.subsonic.restapi.Genre().apply {
                content = genre.name
                songCount = genre.getSongCount()
                albumCount = genre.getAlbumCount()
            }
        }
    }
}
