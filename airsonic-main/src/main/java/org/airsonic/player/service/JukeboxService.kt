package org.airsonic.player.service

import org.airsonic.player.domain.Player
import org.airsonic.player.domain.PlayerTechnology
import org.airsonic.player.domain.PlayQueue

/** Service for controlling a jukebox player. */
open class JukeboxService(private val jukeboxLegacySubsonicService: JukeboxLegacySubsonicService) {

    open fun setGain(airsonicPlayer: Player, gain: Float) {
        if (airsonicPlayer.technology != PlayerTechnology.JUKEBOX) return
        jukeboxLegacySubsonicService.setGain(gain)
    }

    open fun setPosition(airsonicPlayer: Player, positionInSeconds: Int) {
        if (airsonicPlayer.technology != PlayerTechnology.JUKEBOX) return
        throw UnsupportedOperationException()
    }

    open fun getGain(airsonicPlayer: Player): Float = when (airsonicPlayer.technology) {
        PlayerTechnology.JUKEBOX -> jukeboxLegacySubsonicService.gain
        else -> 0f
    }

    /** This method should be removed when the jukebox is controlled only through rest api. */
    @Deprecated("This method should be removed when the jukebox is controlled only through rest api.")
    open fun updateJukebox(airsonicPlayer: Player, offset: Int) {
        if (airsonicPlayer.technology == PlayerTechnology.JUKEBOX) {
            jukeboxLegacySubsonicService.updateJukebox(airsonicPlayer, offset)
        }
    }

    open fun getPosition(airsonicPlayer: Player): Int = when (airsonicPlayer.technology) {
        PlayerTechnology.JUKEBOX -> jukeboxLegacySubsonicService.position
        else -> 0
    }

    /** Plays the playQueue of a jukebox player starting at the first item of the queue. */
    open fun play(airsonicPlayer: Player) {
        if (airsonicPlayer.technology == PlayerTechnology.JUKEBOX) {
            jukeboxLegacySubsonicService.updateJukebox(airsonicPlayer, 0)
        }
    }

    open fun start(airsonicPlayer: Player) = play(airsonicPlayer)

    open fun stop(airsonicPlayer: Player) {
        airsonicPlayer.playQueue?.status = PlayQueue.Status.STOPPED
        if (airsonicPlayer.technology == PlayerTechnology.JUKEBOX) {
            jukeboxLegacySubsonicService.updateJukebox(airsonicPlayer, 0)
        }
    }

    open fun skip(airsonicPlayer: Player, index: Int, offset: Int) {
        if (airsonicPlayer.technology == PlayerTechnology.JUKEBOX) {
            jukeboxLegacySubsonicService.updateJukebox(airsonicPlayer, offset)
        }
    }

    /** This method is only here due to legacy considerations. */
    @Deprecated("This method is only here due to legacy considerations.")
    open fun canControl(airsonicPlayer: Player): Boolean =
        airsonicPlayer.technology == PlayerTechnology.JUKEBOX &&
            jukeboxLegacySubsonicService.player?.id == airsonicPlayer.id
}
