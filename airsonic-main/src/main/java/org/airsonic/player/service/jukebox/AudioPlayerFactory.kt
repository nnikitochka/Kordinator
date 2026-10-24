package org.airsonic.player.service.jukebox

import org.airsonic.player.service.JukeboxLegacySubsonicService
import java.io.InputStream

object AudioPlayerFactory {
    @JvmStatic
    @Throws(Exception::class)
    fun createAudioPlayer(`in`: InputStream, jukeboxLegacySubsonicService: JukeboxLegacySubsonicService): AudioPlayer {
        return AudioPlayer(`in`, jukeboxLegacySubsonicService)
    }
}
