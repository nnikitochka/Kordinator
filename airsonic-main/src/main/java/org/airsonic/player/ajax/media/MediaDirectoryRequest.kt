package org.airsonic.player.ajax.media

data class MediaDirectoryRequest(
    var ids: MutableList<Int>? = null,
    var paths: MutableMap<Int, MutableList<String>>? = null,
)
