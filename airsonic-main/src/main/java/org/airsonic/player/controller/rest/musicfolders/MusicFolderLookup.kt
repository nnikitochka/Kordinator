package org.airsonic.player.controller.rest.musicfolders

import org.airsonic.player.domain.MusicFolder

fun interface MusicFolderLookup {
    fun getMusicFoldersForUser(username: String): List<MusicFolder>
}