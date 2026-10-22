package org.airsonic.player.ajax.bookmark

import org.airsonic.player.ajax.media.MediaFileEntry
import java.time.Instant

data class BookmarkInfo(
    val id: Int,
    val mediaFileEntry: MediaFileEntry?,
    val changed: Instant?,
    val created: Instant?,
    val comment: String?,
    val positionMillis: Long
)
