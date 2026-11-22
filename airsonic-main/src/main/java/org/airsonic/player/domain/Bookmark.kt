package org.airsonic.player.domain

import java.time.Instant

/** A bookmark within a media file, for a given user. */
class Bookmark(
    var id: Int,
    var mediaFileId: Int,
    var positionMillis: Long,
    var username: String,
    var comment: String?,
    var created: Instant?,
    var changed: Instant?
)
