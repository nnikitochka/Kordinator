package org.airsonic.player.ajax.lyrics

data class LyricsGetRequest(
    val song: String = "",
    val artist: String = ""
)
