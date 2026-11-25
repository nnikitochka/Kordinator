package su.nnedition.kord.service.metadata

import com.google.common.io.MoreFiles
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.service.media.MediaFolderService
import org.apache.commons.lang.StringUtils
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.reference.PictureTypes
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.logging.LogManager

/** Reads and writes audio tags using jaudiotagger. */
open class JaudiotaggerParser(private val mediaFolders: MediaFolderService) : MetaDataParser() {
    override fun getRawMetaData(file: Path): MetaData {
        val metadata = MetaData()
        try {
            val audio = AudioFileIO.read(file.toFile())
            audio.tag?.let { tag ->
                metadata.albumName = tagField(tag, FieldKey.ALBUM)
                metadata.title = tagField(tag, FieldKey.TITLE)
                metadata.year = parseIntegerPattern(tagField(tag, FieldKey.YEAR), YEAR_NUMBER_PATTERN)
                metadata.genre = mapGenre(tagField(tag, FieldKey.GENRE))
                metadata.discNumber = parseIntegerPattern(tagField(tag, FieldKey.DISC_NO), null)
                metadata.trackNumber = parseIntegerPattern(tagField(tag, FieldKey.TRACK), TRACK_NUMBER_PATTERN)
                metadata.musicBrainzReleaseId = tagField(tag, FieldKey.MUSICBRAINZ_RELEASEID)
                metadata.musicBrainzRecordingId = tagField(tag, FieldKey.MUSICBRAINZ_TRACK_ID)
                metadata.artist = tagField(tag, FieldKey.ARTIST)
                metadata.albumArtist = tagField(tag, FieldKey.ALBUM_ARTIST)
                if (StringUtils.isBlank(metadata.artist)) metadata.artist = metadata.albumArtist
                if (StringUtils.isBlank(metadata.albumArtist)) metadata.albumArtist = metadata.artist
            }
            audio.audioHeader?.let { header ->
                metadata.variableBitRate = header.isVariableBitRate
                metadata.bitRate = header.bitRateAsNumber.toInt()
                metadata.duration = header.preciseTrackLength
            }
        } catch (failure: Throwable) {
            LOG.warn("Error when parsing tags in {}", file, failure)
        }
        return metadata
    }

    override fun setMetaData(file: MediaFile, metaData: MetaData) {
        try {
            val folder = mediaFolders.getMusicFolderById(file.folderId)!!
            val audio = AudioFileIO.read(file.getFullPath(folder.path).toFile())
            val tag = audio.tagOrCreateAndSetDefault
            tag.setField(FieldKey.ARTIST, StringUtils.trimToEmpty(metaData.artist))
            tag.setField(FieldKey.ALBUM, StringUtils.trimToEmpty(metaData.albumName))
            tag.setField(FieldKey.TITLE, StringUtils.trimToEmpty(metaData.title))
            tag.setField(FieldKey.GENRE, StringUtils.trimToEmpty(metaData.genre))
            try {
                tag.setField(FieldKey.ALBUM_ARTIST, StringUtils.trimToEmpty(metaData.albumArtist))
            } catch (_: Exception) {
                // ID3v1 does not support album artist.
            }
            metaData.trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) } ?: tag.deleteField(FieldKey.TRACK)
            metaData.year?.let { tag.setField(FieldKey.YEAR, it.toString()) } ?: tag.deleteField(FieldKey.YEAR)
            audio.commit()
        } catch (failure: Throwable) {
            LOG.warn("Failed to update tags for file {}", file, failure)
            throw RuntimeException("Failed to update tags for file $file. ${failure.message}", failure)
        }
    }

    override fun isEditingSupported(): Boolean = true
    override fun isApplicable(path: Path): Boolean = Files.isRegularFile(path) &&
        MoreFiles.getFileExtension(path).lowercase(Locale.getDefault()) in APPLICABLE_FORMATS
    override fun getOrder(): Int = 0
    override fun getMediaFolderService(): MediaFolderService = mediaFolders

    open fun getEmbeddedArtwork(file: Path): Artwork? = getArtwork(file)

    companion object {
        private val LOG = LoggerFactory.getLogger(JaudiotaggerParser::class.java)
        private val APPLICABLE_FORMATS = setOf("mp3", "m4a", "m4b", "m4p", "aac", "ogg", "flac", "wav", "mpc", "mp+", "aif", "dsf", "aiff", "wma")

        init {
            try {
                LogManager.getLogManager().reset()
            } catch (failure: Throwable) {
                LOG.warn("Failed to turn off logging from Jaudiotagger.", failure)
            }
        }

        private fun tagField(tag: Tag, field: FieldKey): String? = try {
            StringUtils.replace(StringUtils.trimToNull(tag.getFirst(field)), "\u0000", " ")
        } catch (_: Exception) {
            null
        }

        @JvmStatic
        fun isImageAvailable(file: Path): Boolean = try {
            getArtwork(file) != null
        } catch (failure: Throwable) {
            LOG.info("Failed to find cover art tag in {}", file, failure)
            false
        }

        @JvmStatic
        @Throws(Exception::class)
        fun getArtwork(file: Path): Artwork? {
            val tag = AudioFileIO.read(file.toFile()).tag ?: return null
            return tag.artworkList.stream().filter { it.pictureType == PictureTypes.DEFAULT_ID }
                .findAny().orElse(tag.firstArtwork)
        }
    }
}
