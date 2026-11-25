package org.airsonic.player.service

import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang.ObjectUtils
import org.apache.commons.lang.StringUtils
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.security.CallerContext
import org.airsonic.player.service.metadata.MetaData
import org.airsonic.player.service.metadata.MetaDataParser
import org.airsonic.player.service.metadata.MetaDataParserFactory
import org.slf4j.LoggerFactory

open class TagEditService(
    private var metaDataParserFactory: MetaDataParserFactory,
    private var mediaFileService: MediaFileService,
    private val mediaFolderService: MediaFolderService,
    private val securityService: SecurityService
) {
    /** Updates tags, returning `UPDATED`, `SKIPPED`, or a user-facing error message. */
    open fun setTags(data: TrackTagUpdate, callerContext: CallerContext): String {
        try {
            val file = mediaFileService.getMediaFile(data.mediaFileId)
                ?: throw IllegalArgumentException("Media file not found")
            if (!securityService.isFolderAccessAllowed(file, callerContext)) {
                throw SecurityException("Access denied")
            }
            if (!file.isFile) throw IllegalArgumentException("Media file is not a file")

            val folder: MusicFolder = mediaFolderService.getMusicFolderById(file.folderId)!!
            val filePath = file.getFullPath(folder.path)
            val parser: MetaDataParser? = metaDataParserFactory.getParser(filePath)
            if (parser == null || !parser.isEditingSupported()) {
                return "Tag editing of ${FilenameUtils.getExtension(file.path)} files is not supported."
            }

            val artist = data.artist.trimToNull()
            val album = data.album.trimToNull()
            val title = data.title.trimToNull()
            val genre = data.genre.trimToNull()

            if (StringUtils.equals(artist, file.artist) &&
                StringUtils.equals(album, file.albumName) &&
                StringUtils.equals(title, file.title) &&
                ObjectUtils.equals(data.year, file.year) &&
                StringUtils.equals(genre, file.genre) &&
                ObjectUtils.equals(data.track, file.trackNumber)
            ) {
                return "SKIPPED"
            }

            val newMetaData: MetaData = parser.getMetaData(filePath)
            // Album artist is intentionally unchanged; it is not user-editable.
            newMetaData.artist = artist
            newMetaData.albumName = album
            newMetaData.title = title
            newMetaData.year = data.year
            newMetaData.genre = genre
            newMetaData.trackNumber = data.track
            parser.setMetaData(file, newMetaData)

            mediaFileService.refreshMediaFile(file, folder)
            mediaFileService.refreshMediaFile(mediaFileService.getParentOf(file)!!, folder)
            return "UPDATED"
        } catch (exception: SecurityException) {
            throw exception
        } catch (exception: Exception) {
            LOG.warn("Failed to update tags for {}", data.mediaFileId, exception)
            return exception.message ?: "Tag editing failed"
        }
    }

    open fun setMediaFileService(mediaFileService: MediaFileService) {
        this.mediaFileService = mediaFileService
    }

    open fun setMetaDataParserFactory(metaDataParserFactory: MetaDataParserFactory) {
        this.metaDataParserFactory = metaDataParserFactory
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(TagEditService::class.java)
    }
}

private fun String?.trimToNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)
