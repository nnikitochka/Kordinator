package su.nnedition.kord.data.local.track.entity

import su.nnedition.kord.data.domain.track.Track
import su.nnedition.kord.data.local.artist.model
import su.nnedition.kord.data.local.artist.domain

fun Track.model(): TrackWithArtists = TrackWithArtists(
    track = TrackEntity(
        id = this.id,
        title = this.title,
        relativePath = this.relativePath,
        releaseDate = this.releaseDate,
    ),
    artists = this.artists.map { it.model() },
)

fun TrackWithArtists.domain(): Track = Track(
    id = this.track.id,
    title = this.track.title,
    artists = this.artists.map { it.domain() },
    relativePath = this.track.relativePath,
    releaseDate = this.track.releaseDate,
)