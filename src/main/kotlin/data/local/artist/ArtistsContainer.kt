package su.nnedition.kord.data.local.repository

import kotlinx.coroutines.runBlocking
import su.nnedition.kord.data.domain.artist.Artist
import su.nnedition.kord.data.domain.artist.ArtistRepository
import su.nnedition.kord.data.local.LibraryDatabase
import su.nnedition.kord.data.local.artist.model
import su.nnedition.kord.data.local.artist.domain

class ArtistsContainer : ArtistRepository() {
    private val dao = LibraryDatabase.getOrCreateDatabase().artistsDao

    override suspend fun insertArtist(artist: Artist) {
        dao.insertArtist(artist.model())
    }
    override suspend fun insertArtists(artist: List<Artist>) {
        dao.insertArtists(artist.map { it.model() })
    }

    override suspend fun findArtistById(id: Long): Artist? {
        return dao.findArtistById(id)?.domain()
    }
    override suspend fun findArtistByName(name: String): Artist? {
        return dao.findArtistByName(name)?.domain()
    }

    private var nextId: Long = runBlocking { dao.getArtistsCount() }.inc()
    override fun createId(): Long {
        val id = nextId
        nextId += 1
        return id
    }
}