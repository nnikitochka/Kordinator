package org.airsonic.player.controller.rest.musicfolders

import org.subsonic.restapi.MusicFolders

/** Builds the Subsonic representation of the music folders available to a user. */
class MusicFoldersService(
    private val musicFolderLookup: MusicFolderLookup,
) {
    fun getMusicFolders(username: String): MusicFolders = MusicFolders().also { result ->
        musicFolderLookup.getMusicFoldersForUser(username).forEach { folder ->
            result.musicFolder += org.subsonic.restapi.MusicFolder().apply {
                id = folder.id
                name = folder.name
            }
        }
    }
}
