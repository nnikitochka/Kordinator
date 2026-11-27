package su.nnedition.kord.service

import su.nnedition.kord.model.ReleaseDate

import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang.ObjectUtils
import org.apache.commons.lang.StringUtils
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.security.CallerContext
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.metadata.MetaData
import su.nnedition.kord.service.metadata.MetaDataParser
import su.nnedition.kord.service.metadata.MetaDataParserFactory
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

            val date = when {
                data.updateReleaseDate -> data.releaseDate?.let {
                    requireNotNull(ReleaseDate.parse(it)) { "Invalid release date" }
                }
                data.year != null -> requireNotNull(ReleaseDate.fromYear(data.year)) { "Invalid year" }
                else -> file.releaseDate ?: ReleaseDate.fromYear(file.year)
            }
            val track = if (data.fields?.contains("track") != false) data.track else file.trackNumber
            val changesDate = data.updateReleaseDate || data.year != null
            val artist = if (data.fields?.contains("artist") != false) data.artist.trimToNull() else file.artist
            val album = if (data.fields?.contains("album") != false) data.album.trimToNull() else file.albumName
            val title = if (data.fields?.contains("title") != false) data.title.trimToNull() else file.title
            val genre = if (data.fields?.contains("genre") != false) data.genre.trimToNull() else file.genre

            if (!changesDate && StringUtils.equals(artist, file.artist) &&
                StringUtils.equals(album, file.albumName) &&
                StringUtils.equals(title, file.title) &&
                StringUtils.equals(genre, file.genre) &&
                ObjectUtils.equals(track, file.trackNumber)
            ) {
                return "SKIPPED"
            }

            val newMetaData: MetaData = parser.getMetaData(filePath)
            // Album artist is intentionally unchanged; it is not user-editable.
            newMetaData.artist = artist
            newMetaData.albumName = album
            newMetaData.title = title
            newMetaData.year = date?.year
            newMetaData.releaseDate = date
            newMetaData.updateReleaseDate = changesDate
            newMetaData.genre = genre
            newMetaData.trackNumber = track
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
