package org.airsonic.player.ajax.artist

class ArtistInfoRequest(
    var mediaFileId: Int? = null,
    var maxSimilarArtists: Int? = null,
    var maxTopSongs: Int? = null,
)
