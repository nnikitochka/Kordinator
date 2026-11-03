package org.airsonic.player.service

import org.airsonic.player.command.UserSettingsCommand
import org.airsonic.player.domain.TranscodeScheme
import org.airsonic.player.domain.User
import org.airsonic.player.domain.UserCredential
import org.apache.commons.lang.StringUtils
import java.time.Instant

/** Shared user administration operations for the website and REST API. */
class UserAdministrationService(
    private val security: SecurityService,
    private val settings: SettingsService,
    private val folders: MediaFolderService,
) {
    fun createUser(command: UserSettingsCommand) {
        val user = User(command.username, StringUtils.trimToNull(command.email))
        user.isLdapAuthenticated = command.isLdapAuthenticated
        security.createUser(user, command.password, "Created for new user")
        updateUser(command)
    }

    fun updateUser(command: UserSettingsCommand) {
        val user = security.getUserByName(command.username)
        user.email = StringUtils.trimToNull(command.email)
        user.isLdapAuthenticated = command.isLdapAuthenticated
        user.roles = buildSet {
            if (command.isAdminRole) add(User.Role.ADMIN)
            if (command.isDownloadRole) add(User.Role.DOWNLOAD)
            if (command.isUploadRole) add(User.Role.UPLOAD)
            if (command.isCoverArtRole) add(User.Role.COVERART)
            if (command.isCommentRole) add(User.Role.COMMENT)
            if (command.isStreamRole) add(User.Role.STREAM)
            if (command.isJukeboxRole) add(User.Role.JUKEBOX)
            if (command.isSettingsRole) add(User.Role.SETTINGS)
            if (command.isShareRole) add(User.Role.SHARE)
        }
        security.updateUser(user)
        if (command.isPasswordChange) {
            security.createCredential(UserCredential(user.username, user.username, command.password,
                security.getPreferredPasswordEncoder(true), UserCredential.App.AIRSONIC, "Created by admin"))
        }
        val userSettings = settings.getUserSettings(command.username)
        userSettings.transcodeScheme = TranscodeScheme.valueOf(command.transcodeSchemeName)
        userSettings.changed = Instant.now()
        settings.updateUserSettings(userSettings)
        folders.setMusicFoldersForUser(command.username, command.allowedMusicFolderIds.toSet())
    }
}
