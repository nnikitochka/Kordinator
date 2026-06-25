package su.nnedition.kord.data.local.track

import kotlinx.coroutines.runBlocking
import su.nnedition.kord.data.domain.track.Track
import su.nnedition.kord.data.domain.track.TrackRepository
import su.nnedition.kord.data.local.LibraryDatabase
import su.nnedition.kord.data.local.track.entity.domain
import su.nnedition.kord.data.local.track.entity.model

class TracksContainer : TrackRepository() {
    private val dao = LibraryDatabase.getOrCreateDatabase().tracksDao

    override suspend fun insertTrack(track: Track) {
        dao.insertTrackWithArtists(track.model())
    }

    override suspend fun getTracks(): List<Track> {
        return dao.getTracksWithArtists().map { it.domain() }
    }

    private var nextId: Long = runBlocking { dao.getTracksCount() }.inc()
    override fun createId(): Long {
        val id = nextId
        nextId += 1
        return id
    }
}