package org.airsonic.player.command

import org.airsonic.player.domain.CoverArtSource
import org.airsonic.player.domain.Theme

/** General server settings submitted by the administration API. */
class GeneralSettingsCommand {
    var playlistFolder: String? = null
    var musicFileTypes: String? = null
    var videoFileTypes: String? = null
    var coverArtFileTypes: String? = null
    var coverArtSource: CoverArtSource? = null
    var coverArtQuality: Int? = null
    var coverArtConcurrency: Int? = null
    var index: String? = null
    var ignoredArticles: String? = null
    var genreSeparators: String? = null
    var shortcuts: String? = null
    var sortAlbumsByYear: Boolean = false
    var gettingStartedEnabled: Boolean = false
    var welcomeTitle: String? = null
    var welcomeSubtitle: String? = null
    var welcomeMessage: String? = null
    var loginMessage: String? = null
    var sessionDuration: String? = null
    var localeIndex: String? = null
    var locales: Array<String>? = null
    var themeIndex: String? = null
    var themes: Array<Theme>? = null

    fun isSortAlbumsByYear(): Boolean = sortAlbumsByYear

    fun isGettingStartedEnabled(): Boolean = gettingStartedEnabled

    /** Kept as a no-op for callers that still set this legacy command property. */
    fun setReloadNeeded(@Suppress("UNUSED_PARAMETER") reloadNeeded: Boolean) = Unit
}
