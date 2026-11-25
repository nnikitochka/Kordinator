package su.nnedition.kord.dao

import su.nnedition.kord.domain.Album
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AlbumDaoTest : DaoTestCase() {
    private lateinit var folder: MusicFolder
    private lateinit var album: Album
    private lateinit var fallbackAlbum: Album

    @Before
    fun setUp() {
        folder = MusicFolder(
            Paths.get("/album-dao-${UUID.randomUUID()}"), "Album DAO test", MusicFolder.Type.MEDIA, true, Instant.now()
        )
        daos().musicFolderDao.createMusicFolder(folder)
        album = createAlbum("album/one", "One", "Artist One")
        fallbackAlbum = createAlbum("album/fallback", "Shared Name", "Other Artist")
    }

    @After
    fun tearDown() {
        val albumIds = listOfNotNull(
            if (::album.isInitialized) album.id else null,
            if (::fallbackAlbum.isInitialized) fallbackAlbum.id else null
        )
        albumIds.forEach { id -> getJdbcHelper().update("delete from starred_album where album_id=?", id) }
        albumIds.forEach { id -> getJdbcHelper().update("delete from album where id=?", id) }
        if (::folder.isInitialized && folder.id != null) daos().musicFolderDao.deleteMusicFolder(folder.id!!)
    }

    @Test
    fun `create update lookup and file matching retain album DAO behavior`() {
        assertEquals(album.id, daos().albumDao.getAlbum(assertNotNull(album.id))?.id)
        assertEquals(album.id, daos().albumDao.getAlbum("Artist One", "One")?.id)

        val matchingArtist = MediaFile().apply {
            albumName = "One"
            artist = "Artist One"
            parentPath = "album/one"
            folderId = folder.id
        }
        assertEquals(album.id, daos().albumDao.getAlbumForFile(matchingArtist)?.id)

        val matchingPath = MediaFile().apply {
            albumName = "Shared Name"
            artist = "Unknown Artist"
            parentPath = "album/fallback"
            folderId = folder.id
        }
        assertEquals(fallbackAlbum.id, daos().albumDao.getAlbumForFile(matchingPath)?.id)

        album.comment = "Updated comment"
        daos().albumDao.createOrUpdateAlbum(album)
        assertEquals("Updated comment", daos().albumDao.getAlbum("Artist One", "One")?.comment)
        assertEquals(1, daos().albumDao.getAlbumsForArtist("Artist One", listOf(folder)).size)
    }

    private fun createAlbum(path: String, name: String, artist: String): Album = Album().apply {
        this.path = path
        this.name = name
        this.artist = artist
        this.folderId = folder.id
        this.created = Instant.now()
        this.lastScanned = Instant.now()
        this.isPresent = true
        daos().albumDao.createOrUpdateAlbum(this)
    }
}
