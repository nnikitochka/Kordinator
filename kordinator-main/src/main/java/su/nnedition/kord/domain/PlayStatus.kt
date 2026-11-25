package su.nnedition.kord.domain

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Represents playback of a track, possibly remote (for example, a cached song on a mobile phone).
 * A TransferStatus can play multiple files at different times, each represented by a PlayStatus.
 */
class PlayStatus(
    val transferId: UUID?,
    val mediaFile: MediaFile?,
    val player: Player?,
    val time: Instant?
) {
    constructor(transferId: UUID?, mediaFile: MediaFile?, player: Player?, millisSinceLastUpdate: Long) :
        this(transferId, mediaFile, player, Instant.now().minusMillis(millisSinceLastUpdate))

    fun isExpired(): Boolean = Instant.now().isAfter(time!!.plusMillis(TTL_MILLIS))

    val minutesAgo: Long
        get() = ChronoUnit.MINUTES.between(time!!, Instant.now())

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + (mediaFile?.hashCode() ?: 0)
        result = 31 * result + (player?.id?.hashCode() ?: 0)
        result = 31 * result + (transferId?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as PlayStatus
        val samePlayer = when {
            player == null -> other.player == null
            player.id == null -> other.player != null && other.player.id == null
            else -> other.player?.id == player.id
        }
        return mediaFile == other.mediaFile && samePlayer && transferId == other.transferId
    }

    companion object {
        private const val TTL_MILLIS = 6L * 60L * 60L * 1000L
    }
}
