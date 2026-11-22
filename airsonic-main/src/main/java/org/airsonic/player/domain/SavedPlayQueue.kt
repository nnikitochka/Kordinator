package org.airsonic.player.domain

import java.time.Instant

/**
 * Used to save the play queue state for a user.
 *
 * Can be used to share the play queue (including currently playing track and position within
 * that track) across client apps.
 */
open class SavedPlayQueue(
    var id: Int?,
    var username: String?,
    var mediaFileIds: List<Int>?,
    var currentMediaFileId: Int?,
    var positionMillis: Long?,
    var changed: Instant?,
    var changedBy: String?
)
