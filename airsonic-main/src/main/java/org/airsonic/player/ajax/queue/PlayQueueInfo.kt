package org.airsonic.player.ajax.queue

import org.airsonic.player.ajax.media.MediaFileEntry
import org.airsonic.player.domain.PlayQueue
import org.airsonic.player.domain.PlayQueue.RepeatStatus

/**
 * The playlist of a player.
 */
data class PlayQueueInfo(
    val entries: MutableList<MediaFileEntry>?,
    val playStatus: PlayQueue.Status?,
    val repeatStatus: RepeatStatus?,
    val isShuffleRadioEnabled: Boolean,
    val gain: Float
) {
    var revision: Long = 0
    var startPlayerAt: Int = -1
        private set
    var startPlayerAtPosition: Long = 0 // millis
        private set

    fun setStartPlayerAt(startPlayerAt: Int): PlayQueueInfo {
        this.startPlayerAt = startPlayerAt
        return this
    }

    fun setStartPlayerAtPosition(startPlayerAtPosition: Long): PlayQueueInfo {
        this.startPlayerAtPosition = startPlayerAtPosition
        return this
    }
}
