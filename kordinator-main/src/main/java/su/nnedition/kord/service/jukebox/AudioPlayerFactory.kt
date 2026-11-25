package su.nnedition.kord.service.jukebox

import su.nnedition.kord.service.JukeboxLegacySubsonicService
import java.io.InputStream

object AudioPlayerFactory {
    @JvmStatic
    @Throws(Exception::class)
    fun createAudioPlayer(`in`: InputStream, jukeboxLegacySubsonicService: JukeboxLegacySubsonicService): AudioPlayer {
        return AudioPlayer(`in`, jukeboxLegacySubsonicService)
    }
}
