package org.airsonic.player.ajax.bookmark

data class BookmarkCreateRequest(
    var positionMillis: Long? = null,
    var comment: String? = null,
    var mediaFileId: Int? = null,
)
