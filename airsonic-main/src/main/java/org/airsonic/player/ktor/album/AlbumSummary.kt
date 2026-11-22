package org.airsonic.player.ktor.album

data class AlbumSummary(
    val id: Int,
    val name: String?,
    val artist: String?,
    val year: Int?,
    val genre: String?,
    val durationSeconds: Int,
)
