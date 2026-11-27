package su.nnedition.kord.service.metadata

import su.nnedition.kord.model.ReleaseDate

import java.util.Collections

/** Tags and stream information read from a media file. */
class MetaData {
    var discNumber: Int? = null
    var trackNumber: Int? = null
    var title: String? = null
    var artist: String? = null
    var albumArtist: String? = null
    var albumName: String? = null
    var genre: String? = null
    var year: Int? = null
    var releaseDate: ReleaseDate? = null
    /** Only an explicit tag-edit request may replace or delete YEAR. */
    var updateReleaseDate: Boolean = false
    var bitRate: Int? = null
    var variableBitRate: Boolean = false
    var duration: Double? = null
    var width: Int? = null
    var height: Int? = null
    var musicBrainzReleaseId: String? = null
    var musicBrainzRecordingId: String? = null

    private val mutableTracks = ArrayList<Track>()
    val tracks: List<Track> = Collections.unmodifiableList(mutableTracks)
    val audioTracks: List<Track> get() = mutableTracks.filter { it.isAudio }.toMutableList()
    val videoTracks: List<Track> get() = mutableTracks.filter { it.isVideo }.toMutableList()
    val subtitleTracks: List<Track> get() = mutableTracks.filter { it.isSubtitle }.toMutableList()

    fun addTrack(track: Track) {
        mutableTracks.add(track)
    }
}
