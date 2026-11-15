package org.airsonic.player.controller.rest.cover

import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService

/** Supplies the initial values for the existing change-cover-art page. */
class ChangeCoverArtPageController(
    private val mediaFiles: MediaFileService,
    private val folders: MediaFolderService,
) {
    fun page(username: String, id: Int, requestedArtist: String?, requestedAlbum: String?): Map<String, Any?> {
        val directory = mediaFiles.getMediaFile(id) ?: throw NoSuchElementException("Media file not found")
        if (!directory.isDirectory || folders.getMusicFoldersForUser(username).none { it.id == directory.folderId }) {
            throw SecurityException("Cover art access denied")
        }
        return mapOf<String, Any?>(
            "id" to id,
            "artist" to (requestedArtist?.takeUnless { it.isBlank() } ?: directory.artist),
            "album" to (requestedAlbum?.takeUnless { it.isBlank() } ?: directory.albumName),
        )
    }
}
