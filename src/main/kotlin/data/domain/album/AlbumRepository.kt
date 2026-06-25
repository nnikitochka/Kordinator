package su.nnedition.kord.data.domain.album

abstract class AlbumRepository {
    abstract suspend fun insertAlbum(album: Album)

    abstract fun createId(): Long
}