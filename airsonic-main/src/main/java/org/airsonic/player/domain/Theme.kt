package org.airsonic.player.domain

/**
 * Contains the ID and name for a theme.
 */
data class Theme @JvmOverloads constructor(
    val id: String,
    val name: String,
    val parent: String? = null
)
