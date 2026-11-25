package su.nnedition.kord.service.metadata

import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.service.media.MediaFolderService
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang.StringUtils
import org.jaudiotagger.tag.reference.GenreTypes
import java.nio.file.Path
import java.util.SortedSet
import java.util.TreeSet
import java.util.regex.Pattern

/** Common metadata fallback and tag parsing for media parsers. */
abstract class MetaDataParser {
    fun getMetaData(file: Path): MetaData {
        val metadata = getRawMetaData(file)
        val artist = metadata.artist ?: guessArtist(file)
        metadata.albumArtist = metadata.albumArtist ?: guessArtist(file)
        metadata.albumName = metadata.albumName ?: guessAlbum(file, artist)
        val title = metadata.title ?: guessTitle(file)
        metadata.artist = artist
        metadata.title = removeTrackNumberFromTitle(title, metadata.trackNumber)
        return metadata
    }

    abstract fun getRawMetaData(file: Path): MetaData
    abstract fun setMetaData(file: MediaFile, metaData: MetaData)
    abstract fun isApplicable(path: Path): Boolean
    abstract fun isEditingSupported(): Boolean
    open fun getOrder(): Int = Int.MAX_VALUE
    abstract fun getMediaFolderService(): MediaFolderService

    fun guessArtist(file: Path): String? {
        val parent = file.parent
        if (isRoot(parent)) return null
        val grandParent = parent.parent
        return if (isRoot(grandParent)) null else grandParent.fileName.toString()
    }

    fun guessAlbum(file: Path, artist: String?): String? {
        val parent = file.parent
        val album = if (isRoot(parent)) null else parent.fileName.toString()
        return if (artist != null && album != null) album.replace("$artist - ", "") else album
    }

    private fun isRoot(file: Path): Boolean = getMediaFolderService().getAllMusicFolders(false, true)
        .parallelStream().anyMatch { folder -> file == folder.path }

    fun mapGenre(genre: String?): String? {
        if (genre == null) return null
        val matcher = GENRE_PATTERN.matcher(genre)
        if (matcher.matches()) {
            val genreId = matcher.group(1).toInt()
            if (genreId >= 0 && genreId < GenreTypes.getInstanceOf().size) {
                return GenreTypes.getInstanceOf().getValueForId(genreId)
            }
        }
        return genre
    }

    fun parseTrackNumber(value: String?): Int? = parseRawIntegerPattern(value, TRACK_NUMBER_PATTERN)
    fun parseYear(value: String?): Int? = parseRawIntegerPattern(value, YEAR_NUMBER_PATTERN)
    fun parseInteger(value: String?): Int? = StringUtils.trimToNull(value)?.toIntOrNull()?.takeUnless { it == 0 }
    fun parseIntegerPattern(value: String?, pattern: Pattern?): Int? = parseRawIntegerPattern(StringUtils.trimToNull(value), pattern)

    private fun parseRawIntegerPattern(value: String?, pattern: Pattern?): Int? {
        if (value == null) return null
        val number = value.toIntOrNull() ?: pattern?.matcher(value)?.let {
            if (it.matches()) it.group(1).toIntOrNull() else null
        }
        return number?.takeUnless { it == 0 }
    }

    fun removeTrackNumberFromTitle(title: String, trackNumber: Int?): String {
        val trimmed = title.trim { it <= ' ' }
        if (trackNumber == null || !trimmed.matches(Regex("0?$trackNumber[. -].*"))) return trimmed
        val result = trimmed.replaceFirst(Regex("^\\d{2}[. -]+"), "")
        return result.ifEmpty { trimmed }
    }

    companion object {
        @JvmField protected val GENRE_PATTERN: Pattern = Pattern.compile("\\((\\d+)\\).*")
        @JvmField protected val TRACK_NUMBER_PATTERN: Pattern = Pattern.compile("(\\d+)/\\d+")
        @JvmField protected val YEAR_NUMBER_PATTERN: Pattern = Pattern.compile("(\\d{4}).*")

        @JvmStatic fun guessTitle(file: Path): String = StringUtils.trim(FilenameUtils.getBaseName(file.toString()))
        @JvmStatic fun getID3V1Genres(): SortedSet<String> = TreeSet(GenreTypes.getInstanceOf().alphabeticalValueList)
    }
}
