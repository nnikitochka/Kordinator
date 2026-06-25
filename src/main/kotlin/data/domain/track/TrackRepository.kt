package su.nnedition.kord.data.domain.track

abstract class TrackRepository {
    abstract suspend fun insertTrack(track: Track)

    abstract suspend fun getTracks(): List<Track>

    abstract fun createId(): Long
}