package su.nnedition.kord.api.module.user.profile

import org.airsonic.player.domain.UserSettings

data class UpdateUserSettingsRequest(
    val themeId: String? = null,
    val locale: String? = null,
    val defaultAlbumList: String? = null,
    val transcodeScheme: String? = null,
    val showNowPlayingEnabled: Boolean? = null,
    val showArtistInfoEnabled: Boolean? = null,
    val finalVersionNotificationEnabled: Boolean? = null,
    val betaVersionNotificationEnabled: Boolean? = null,
    val songNotificationEnabled: Boolean? = null,
    val keyboardShortcutsEnabled: Boolean? = null,
    val autoHidePlayQueue: Boolean? = null,
    val showSideBar: Boolean? = null,
    val viewAsList: Boolean? = null,
    val queueFollowingSongs: Boolean? = null,
    val lastFmEnabled: Boolean? = null,
    val listenBrainzEnabled: Boolean? = null,
    val listenBrainzUrl: String? = null,
    val partyModeEnabled: Boolean? = null,
    val nowPlayingAllowed: Boolean? = null,
    val paginationSizeFiles: Int? = null,
    val paginationSizeFolders: Int? = null,
    val paginationSizePlaylist: Int? = null,
    val paginationSizePlayqueue: Int? = null,
    val paginationSizeBookmarks: Int? = null,
    val autoBookmark: Boolean? = null,
    val audioBookmarkFrequency: Int? = null,
    val videoBookmarkFrequency: Int? = null,
    val searchCount: Int? = null,
    val mainVisibility: UserSettings.Visibility? = null,
    val playlistVisibility: UserSettings.Visibility? = null,
    val playqueueVisibility: UserSettings.Visibility? = null,
) {
    fun hasChanges() = listOf(
        themeId, locale, defaultAlbumList, transcodeScheme, showNowPlayingEnabled, showArtistInfoEnabled,
        finalVersionNotificationEnabled, betaVersionNotificationEnabled, songNotificationEnabled,
        keyboardShortcutsEnabled, autoHidePlayQueue, showSideBar, viewAsList, queueFollowingSongs,
        lastFmEnabled, listenBrainzEnabled, listenBrainzUrl, partyModeEnabled, nowPlayingAllowed,
        paginationSizeFiles, paginationSizeFolders, paginationSizePlaylist, paginationSizePlayqueue,
        paginationSizeBookmarks, autoBookmark, audioBookmarkFrequency, videoBookmarkFrequency,
        searchCount, mainVisibility, playlistVisibility, playqueueVisibility,
    ).any { it != null }
}
