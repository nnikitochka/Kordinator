package org.airsonic.player.service.metadata

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.media.MediaFolderService
import java.nio.file.Files
import java.nio.file.Path

/** Guesses artist, album and title from a media file's path. */
class DefaultMetaDataParser(private val mediaFolders: MediaFolderService) : MetaDataParser() {
    override fun getRawMetaData(file: Path): MetaData {
        val artist = guessArtist(file)
        return MetaData().apply {
            this.artist = artist
            albumArtist = artist
            albumName = guessAlbum(file, artist)
            title = guessTitle(file)
        }
    }

    override fun setMetaData(file: MediaFile, metaData: MetaData) = Unit

    override fun isEditingSupported(): Boolean = false

    override fun getOrder(): Int = 200

    override fun getMediaFolderService(): MediaFolderService = mediaFolders

    override fun isApplicable(path: Path): Boolean = Files.isRegularFile(path)
}
