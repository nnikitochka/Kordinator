package su.nnedition.kord.dao

import su.nnedition.kord.domain.Artist
import su.nnedition.kord.domain.MusicFolder
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtistDaoTest : DaoTestCase() {
    private lateinit var folder: MusicFolder
    private lateinit var artist: Artist

    @Before
    fun setUp() {
        folder = MusicFolder(
            Paths.get("/artist-dao-${UUID.randomUUID()}"), "Artist DAO test", MusicFolder.Type.MEDIA, true, Instant.now()
        )
        daos().musicFolderDao.createMusicFolder(folder)
        artist = Artist().apply {
            name = "Artist ${UUID.randomUUID()}"
            lastScanned = Instant.now()
            isPresent = true
            folderId = folder.id
        }
        daos().artistDao.createOrUpdateArtist(artist)
    }

    @After
    fun tearDown() {
        if (::artist.isInitialized && artist.id != 0) {
            getJdbcHelper().update("delete from starred_artist where artist_id=?", artist.id)
            getJdbcHelper().update("delete from artist where id=?", artist.id)
        }
        if (::folder.isInitialized && folder.id != null) daos().musicFolderDao.deleteMusicFolder(folder.id!!)
    }

    @Test
    fun `create lookup update scan state and listing retain artist DAO behavior`() {
        val dao = daos().artistDao
        val id = artist.id
        assertEquals(id, dao.getArtist(artist.name!!)?.id)
        assertEquals(artist.name, dao.getArtist(id)?.name)
        assertEquals(id, dao.getArtist(artist.name!!, listOf(folder))?.id)
        assertNull(dao.getArtist(artist.name!!, emptyList()))
        assertTrue(dao.getAlphabetialArtists(0, 100, listOf(folder)).any { it.id == id })

        artist.albumCount = 4
        dao.createOrUpdateArtist(artist)
        assertEquals(4, dao.getArtist(id)?.albumCount)

        val scanBoundary = Instant.now().plusSeconds(1)
        dao.markNonPresent(scanBoundary)
        assertTrue(id in dao.getExpungeCandidates())
        assertFalse(dao.getAlphabetialArtists(0, 100, listOf(folder)).any { it.id == id })

        dao.markPresent(artist.name!!, Instant.now())
        assertFalse(id in dao.getExpungeCandidates())
        assertEquals(id, assertNotNull(dao.getArtist(artist.name!!, listOf(folder))).id)
    }
}
