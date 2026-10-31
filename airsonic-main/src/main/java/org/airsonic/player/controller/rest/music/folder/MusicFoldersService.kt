package org.airsonic.player.controller.rest.music.folder

import org.subsonic.restapi.MusicFolder
import org.subsonic.restapi.MusicFolders

/** Builds the Subsonic representation of the music folders available to a user. */
class MusicFoldersService(
    private val musicFolderLookup: MusicFolderLookup,
) {
    fun getMusicFolders(username: String): MusicFolders = MusicFolders().also { result ->
        musicFolderLookup.getMusicFoldersForUser(username).forEach { folder ->
            result.musicFolder += MusicFolder().apply {
                id = folder.id
                name = folder.name
            }
        }
    }
}
