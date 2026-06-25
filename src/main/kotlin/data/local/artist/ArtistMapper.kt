package su.nnedition.kord.data.local.artist

import su.nnedition.kord.data.domain.Genre
import su.nnedition.kord.data.domain.artist.Artist

fun Artist.model() = ArtistEntity(
    id = this.id,
    name = this.name,
    genresRaw = this.genres.joinToString(", ") { it.raw }
)

fun ArtistEntity.domain() = Artist(
    id = this.id,
    name = this.name,
    genres = this.genresRaw.split(", ").map { Genre(it) }.toSet(),
)