package org.airsonic.player.ajax.playlists

data class PlaylistFilesModificationRequest(
    var id: Int? = null,
    var modifierIds: MutableList<Int>? = null,
)
