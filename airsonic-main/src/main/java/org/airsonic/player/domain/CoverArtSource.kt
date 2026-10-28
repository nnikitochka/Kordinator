package org.airsonic.player.domain

/**
 * Enumeration of cover art source. Each value represents a method of how to
 * handle scanning the cover art and which kind of cover art should be prefered
 */
enum class CoverArtSource {
    FILE_TAG, TAG_FILE, FILE, TAG
}
