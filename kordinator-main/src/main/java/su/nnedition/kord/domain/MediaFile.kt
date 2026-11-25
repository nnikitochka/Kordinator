package su.nnedition.kord.domain

import org.apache.commons.io.FilenameUtils
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.Objects
import java.util.function.Function

/** A media file (audio, video or directory) and its metadata. */
open class MediaFile : MediaFileMetadata {
    open var id: Int? = null
    open var path: String? = null
    open var folderId: Int? = null
    open var mediaType: MediaType? = null
    private var version: Int = 0

    constructor() : super()

    constructor(
        id: Int?, path: String?, folderId: Int?, mediaType: MediaType?, format: String?, title: String?,
        albumName: String?, artist: String?, albumArtist: String?, discNumber: Int?, trackNumber: Int?, year: Int?,
        genre: String?, bitRate: Int?, variableBitRate: Boolean, duration: Double?, fileSize: Long?, width: Int?,
        height: Int?, parentPath: String?, playCount: Int, lastPlayed: Instant?, comment: String?, created: Instant?,
        changed: Instant?, lastScanned: Instant?, childrenLastUpdated: Instant?, present: Boolean, version: Int,
        musicBrainzReleaseId: String?, musicBrainzRecordingId: String?
    ) : super() {
        this.id = id
        this.path = path
        this.folderId = folderId
        this.mediaType = mediaType
        this.format = format
        this.title = title
        this.albumName = albumName
        this.artist = artist
        this.albumArtist = albumArtist
        this.discNumber = discNumber
        this.trackNumber = trackNumber
        this.year = year
        this.genre = genre
        this.bitRate = bitRate
        this.variableBitRate = variableBitRate
        this.duration = duration
        this.fileSize = fileSize
        this.width = width
        this.height = height
        this.parentPath = parentPath
        this.playCount = playCount
        this.lastPlayed = lastPlayed
        this.comment = comment
        this.created = created
        this.changed = changed
        this.lastScanned = lastScanned
        this.childrenLastUpdated = childrenLastUpdated
        this.present = present
        this.version = version
        this.musicBrainzReleaseId = musicBrainzReleaseId
        this.musicBrainzRecordingId = musicBrainzRecordingId
    }

    open val relativePath: Path
        get() = Paths.get(path!!)

    open fun getFullPath(relativeMediaFolderPath: Path): Path = relativeMediaFolderPath.resolve(path!!)

    open val isVideo: Boolean
        get() = mediaType == MediaType.VIDEO

    open val isAudio: Boolean
        get() = mediaType == MediaType.MUSIC || mediaType == MediaType.AUDIOBOOK

    open val isDirectory: Boolean
        get() = !isFile

    open val isFile: Boolean
        get() = mediaType != MediaType.DIRECTORY && mediaType != MediaType.ALBUM

    open val isAlbum: Boolean
        get() = mediaType == MediaType.ALBUM

    open val name: String
        get() = title ?: if (isDirectory) FilenameUtils.getName(path) else FilenameUtils.getBaseName(path)

    open fun getVersion(): Int = version

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as MediaFile
        if (path != other.path) return false
        return folderId!!.equals(other.folderId)
    }

    override fun hashCode(): Int = Objects.hash(path, folderId)

    override fun toString(): String = name

    enum class MediaType {
        MUSIC,
        AUDIOBOOK,
        VIDEO,
        DIRECTORY,
        ALBUM
    }

    companion object {
        @JvmStatic
        fun toIdList(from: List<MediaFile>): List<Int?> = from.map { it.id }

        @JvmStatic
        fun toId(): Function<MediaFile, Int?> = Function { it.id }
    }
}
