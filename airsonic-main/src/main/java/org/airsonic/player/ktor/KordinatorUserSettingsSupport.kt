package org.airsonic.player.ktor

import org.airsonic.player.domain.UserSettings

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

