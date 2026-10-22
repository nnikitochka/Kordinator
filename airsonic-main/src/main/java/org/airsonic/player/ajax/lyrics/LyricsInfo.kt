package org.airsonic.player.ajax.lyrics

data class LyricsInfo @JvmOverloads constructor(
    val lyrics: String? = null,
    val artist: String? = null,
    val title: String? = null
) {
    var isTryLater: Boolean? = null
}
