package su.nnedition.kord.api.support

import su.nnedition.kord.domain.UserSettings
import su.nnedition.kord.api.module.user.profile.UserSettingsResponse

internal fun UserSettings.toUserSettingsResponse() = UserSettingsResponse(
    username, locale?.toLanguageTag(), themeId, defaultAlbumList.id, transcodeScheme.name, selectedMusicFolderId,
    showNowPlayingEnabled, showArtistInfoEnabled, nowPlayingAllowed, partyModeEnabled, queueFollowingSongs,
    finalVersionNotificationEnabled, betaVersionNotificationEnabled, songNotificationEnabled,
    keyboardShortcutsEnabled, autoHidePlayQueue, showSideBar, viewAsList, lastFmEnabled, listenBrainzEnabled,
    listenBrainzUrl, avatarScheme.name, systemAvatarId, paginationSizeFiles, paginationSizeFolders,
    paginationSizePlaylist, paginationSizePlayqueue, paginationSizeBookmarks, autoBookmark,
    audioBookmarkFrequency, videoBookmarkFrequency, searchCount, mainVisibility, playlistVisibility,
    playqueueVisibility, changed.toString(),
)
