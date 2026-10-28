package org.airsonic.player.domain

/**
 * Enumeration of player technologies.
 */
enum class PlayerTechnology {
    /**
     * Plays music directly in the web browser using the integrated Flash player.
     */
    WEB,

    /**
     * Plays music in an external player, such as WinAmp or Windows Media Player.
     */
    EXTERNAL,

    /**
     * Same as above, but the playlist is managed by the player, rather than the Airsonic server.
     * In this mode, skipping within songs is possible.
     */
    EXTERNAL_WITH_PLAYLIST,

    /**
     * Plays music directly on the audio device of the Airsonic server.
     */
    JUKEBOX
}