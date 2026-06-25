package su.nnedition.kord.data.domain.artist

abstract class ArtistRepository {
    abstract suspend fun insertArtist(artist: Artist)
    abstract suspend fun insertArtists(artist: List<Artist>)

    abstract suspend fun findArtistById(id: Long): Artist?
    abstract suspend fun findArtistByName(name: String): Artist?

    abstract fun createId(): Long
}