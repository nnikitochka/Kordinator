package su.nnedition.kord.runtime

import su.nnedition.kord.dao.*

data class DaoGraph(
    val albumDao: AlbumDao,
    val artistDao: ArtistDao,
    val avatarDao: AvatarDao,
    val bookmarkDao: BookmarkDao,
    val coverArtDao: CoverArtDao,
    val databaseDao: DatabaseDao,
    val mediaFileDao: MediaFileDao,
    val musicFolderDao: MusicFolderDao,
    val playQueueDao: PlayQueueDao,
    val playlistDao: PlaylistDao,
    val ratingDao: RatingDao,
    val shareDao: ShareDao,
    val transcodingDao: TranscodingDao,
    val userDao: UserDao,
)

/** Builds the DAO layer on a shared JDBC helper and transaction manager. */
class DaoFactory {
    fun create(jdbcHelper: JdbcHelper, transactionManager: TransactionManager): DaoGraph {
        val bookmarkDao = BookmarkDao(jdbcHelper, transactionManager).apply { register() }
        return DaoGraph(
            albumDao = AlbumDao(jdbcHelper),
            artistDao = ArtistDao(jdbcHelper),
            avatarDao = AvatarDao(jdbcHelper),
            bookmarkDao = bookmarkDao,
            coverArtDao = CoverArtDao(jdbcHelper).apply { register() },
            databaseDao = DatabaseDao(jdbcHelper),
            mediaFileDao = MediaFileDao(jdbcHelper),
            musicFolderDao = MusicFolderDao(jdbcHelper).apply { register() },
            playQueueDao = PlayQueueDao(jdbcHelper).apply { register() },
            playlistDao = PlaylistDao(jdbcHelper).apply { register() },
            ratingDao = RatingDao(jdbcHelper),
            shareDao = ShareDao(jdbcHelper).apply { register() },
            transcodingDao = TranscodingDao(jdbcHelper).apply { register() },
            userDao = UserDao(jdbcHelper),
        )
    }
}
