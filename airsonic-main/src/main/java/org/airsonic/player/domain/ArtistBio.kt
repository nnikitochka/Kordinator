package org.airsonic.player.domain

data class ArtistBio(
    val biography: String,
    val musicBrainzId: String?,
    val lastFmUrl: String?,
    val smallImageUrl: String?,
    val mediumImageUrl: String?,
    val largeImageUrl: String?
)
