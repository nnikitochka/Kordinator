package org.airsonic.player.ajax.playlists

data class PlaylistUpdateRequest(
    var id: Int? = null,
    var name: String? = null,
    var comment: String? = null,
    var shared: Boolean? = null,
)
