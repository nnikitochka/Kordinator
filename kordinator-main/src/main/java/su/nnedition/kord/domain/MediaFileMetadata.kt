package su.nnedition.kord.domain

import su.nnedition.kord.model.ReleaseDate
import java.time.Instant

/** Mutable metadata shared by the persisted media-file model. */
open class MediaFileMetadata {
    open var format: String? = null
    open var title: String? = null
    open var albumName: String? = null
    open var artist: String? = null
    open var albumArtist: String? = null
    open var discNumber: Int? = null
    open var trackNumber: Int? = null
    open var year: Int? = null
    open var releaseDate: ReleaseDate? = null
    open var genre: String? = null
    open var bitRate: Int? = null
    open var variableBitRate: Boolean = false
    open var duration: Double? = null
    open var fileSize: Long? = null
    open var width: Int? = null
    open var height: Int? = null
    open var parentPath: String? = null
    open var playCount: Int = 0
    open var lastPlayed: Instant? = null
    open var comment: String? = null
    open var created: Instant? = null
    open var changed: Instant? = null
    open var lastScanned: Instant? = null
    open var starredDate: Instant? = null
    open var childrenLastUpdated: Instant? = null
    open var present: Boolean = false
    open var musicBrainzReleaseId: String? = null
    open var musicBrainzRecordingId: String? = null
    open var art: CoverArt? = null

    open val isVariableBitRate: Boolean
        get() = variableBitRate

    open val isPresent: Boolean
        get() = present

}
