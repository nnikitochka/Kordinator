package org.airsonic.player.domain

/**
 * Enumeration of avatar schemes.
 */
enum class AvatarScheme(
    val code: Int
) {
    /**
     * No avatar should be displayed.
     */
    NONE(-1),

    /**
     * One of the system avatars should be displayed.
     */
    SYSTEM(0),

    /**
     * The custom avatar should be displayed.
     */
    CUSTOM(-2)
}