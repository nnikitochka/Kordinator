package org.airsonic.player.domain

import org.apache.commons.lang.StringUtils
import java.time.Instant

/** Represents a remote player and its playback configuration. */
open class Player {
    open var id: Int? = null
    open var name: String? = null
    open var technology: PlayerTechnology = PlayerTechnology.WEB
    open var clientId: String? = null
    open var type: String? = null
    open var username: String? = null
    open var ipAddress: String? = null
    open var dynamicIp: Boolean = true
    open var autoControlEnabled: Boolean = true
    open var m3uBomEnabled: Boolean = true
    open var lastSeen: Instant? = null
    open var transcodeScheme: TranscodeScheme = TranscodeScheme.OFF
    open var playQueue: PlayQueue? = null

    open fun isJukebox(): Boolean = technology == PlayerTechnology.JUKEBOX

    open fun isExternal(): Boolean = technology == PlayerTechnology.EXTERNAL

    open fun isExternalWithPlaylist(): Boolean = technology == PlayerTechnology.EXTERNAL_WITH_PLAYLIST

    open fun isWeb(): Boolean = technology == PlayerTechnology.WEB

    open val description: String
        get() = buildString {
            append(name ?: "Player $id")
            append(" [").append(username).append(']')
        }

    open val shortDescription: String
        get() = if (StringUtils.isNotBlank(name)) name!! else "Player $id"

    override fun toString(): String = description
}
