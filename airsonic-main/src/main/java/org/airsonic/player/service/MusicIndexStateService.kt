package org.airsonic.player.service

import org.airsonic.player.util.FileUtil
import java.time.Instant

/** Tracks the selected music folder and the modification time used by getIndexes. */
class MusicIndexStateService(
    private val scanner: MediaScannerService,
    private val settings: SettingsService,
    private val folders: MediaFolderService,
) {
    fun selectMusicFolder(username: String, folderId: Int?): Boolean {
        if (folderId == null) return false
        val userSettings = settings.getUserSettings(username)
        // Keep the existing change time: changing the selection must not invalidate the index cache.
        userSettings.selectedMusicFolderId = folderId
        settings.updateUserSettings(userSettings)
        return true
    }

    fun lastModified(username: String, folderId: Int?): Long {
        selectMusicFolder(username, folderId)
        if (scanner.isScanning) return -1L
        val userSettings = settings.getUserSettings(username)
        val allowedFolders = folders.getMusicFoldersForUser(username)
        val selected = allowedFolders.find { it.id == userSettings.selectedMusicFolderId }
        var modified = maxOf(LAST_COMPATIBILITY_TIME, settings.settingsChanged)
        for (folder in if (selected == null) allowedFolders else listOf(selected)) {
            modified = maxOf(modified, FileUtil.lastModified(folder.path).toEpochMilli())
        }
        for (folder in allowedFolders) modified = maxOf(modified, folder.changed.toEpochMilli())
        return maxOf(modified, userSettings.changed.toEpochMilli())
    }

    companion object {
        private val LAST_COMPATIBILITY_TIME = Instant.parse("2012-03-06T00:00:00.00Z").toEpochMilli()
    }
}
