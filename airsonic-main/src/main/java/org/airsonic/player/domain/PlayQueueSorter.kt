package org.airsonic.player.domain

import org.apache.commons.lang.StringUtils

/** Builds comparators for the supported queue sort orders. */
internal object PlayQueueSorter {
    fun comparator(sortOrder: PlayQueue.SortOrder): Comparator<MediaFile> = Comparator { left, right ->
        when (sortOrder) {
            PlayQueue.SortOrder.TRACK -> (left.trackNumber ?: 0).compareTo(right.trackNumber ?: 0)
            PlayQueue.SortOrder.ARTIST -> StringUtils.trimToEmpty(left.artist).compareTo(StringUtils.trimToEmpty(right.artist))
            PlayQueue.SortOrder.ALBUM -> StringUtils.trimToEmpty(left.albumName).compareTo(StringUtils.trimToEmpty(right.albumName))
        }
    }
}
