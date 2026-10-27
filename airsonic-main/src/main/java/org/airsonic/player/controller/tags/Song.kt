package org.airsonic.player.controller.tags

/**
 * Contains information about a single song.
 */
data class Song(
    val id: Int,
    val fileName: String,
    val suggestedTrack: Int,
    val track: Int,
    val suggestedTitle: String,
    val title: String,
    val artist: String,
    val album: String,
    val year: Int,
    val genre: String,
)
