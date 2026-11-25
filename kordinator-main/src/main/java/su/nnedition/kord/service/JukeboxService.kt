package su.nnedition.kord.service

import su.nnedition.kord.domain.Player
import su.nnedition.kord.domain.PlayerTechnology
import su.nnedition.kord.domain.PlayQueue

/** Service for controlling a jukebox player. */
open class JukeboxService(private val jukeboxLegacySubsonicService: JukeboxLegacySubsonicService) {

    open fun setGain(kordinatorPlayer: Player, gain: Float) {
        if (kordinatorPlayer.technology != PlayerTechnology.JUKEBOX) return
        jukeboxLegacySubsonicService.setGain(gain)
    }

    open fun setPosition(kordinatorPlayer: Player, positionInSeconds: Int) {
        if (kordinatorPlayer.technology != PlayerTechnology.JUKEBOX) return
        throw UnsupportedOperationException()
    }

    open fun getGain(kordinatorPlayer: Player): Float = when (kordinatorPlayer.technology) {
        PlayerTechnology.JUKEBOX -> jukeboxLegacySubsonicService.gain
        else -> 0f
    }

    /** This method should be removed when the jukebox is controlled only through rest api. */
    @Deprecated("This method should be removed when the jukebox is controlled only through rest api.")
    open fun updateJukebox(kordinatorPlayer: Player, offset: Int) {
        if (kordinatorPlayer.technology == PlayerTechnology.JUKEBOX) {
            jukeboxLegacySubsonicService.updateJukebox(kordinatorPlayer, offset)
        }
    }

    open fun getPosition(kordinatorPlayer: Player): Int = when (kordinatorPlayer.technology) {
        PlayerTechnology.JUKEBOX -> jukeboxLegacySubsonicService.position
        else -> 0
    }

    /** Plays the playQueue of a jukebox player starting at the first item of the queue. */
    open fun play(kordinatorPlayer: Player) {
        if (kordinatorPlayer.technology == PlayerTechnology.JUKEBOX) {
            jukeboxLegacySubsonicService.updateJukebox(kordinatorPlayer, 0)
        }
    }

    open fun start(kordinatorPlayer: Player) = play(kordinatorPlayer)

    open fun stop(kordinatorPlayer: Player) {
        kordinatorPlayer.playQueue?.status = PlayQueue.Status.STOPPED
        if (kordinatorPlayer.technology == PlayerTechnology.JUKEBOX) {
            jukeboxLegacySubsonicService.updateJukebox(kordinatorPlayer, 0)
        }
    }

    open fun skip(kordinatorPlayer: Player, index: Int, offset: Int) {
        if (kordinatorPlayer.technology == PlayerTechnology.JUKEBOX) {
            jukeboxLegacySubsonicService.updateJukebox(kordinatorPlayer, offset)
        }
    }

    /** This method is only here due to legacy considerations. */
    @Deprecated("This method is only here due to legacy considerations.")
    open fun canControl(kordinatorPlayer: Player): Boolean =
        kordinatorPlayer.technology == PlayerTechnology.JUKEBOX &&
            jukeboxLegacySubsonicService.player?.id == kordinatorPlayer.id
}
