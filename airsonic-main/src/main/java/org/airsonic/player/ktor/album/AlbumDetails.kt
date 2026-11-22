package org.airsonic.player.ktor.album

import org.airsonic.player.ktor.KordinatorTrackResponse

data class AlbumDetails(
    val id: Int,
    val name: String?,
    val artist: String?,
    val year: Int?,
    val genre: String?,
    val trackCount: Int,
    val durationSeconds: Int,
    val tracks: List<KordinatorTrackResponse>,
)