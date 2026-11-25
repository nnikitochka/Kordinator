package su.nnedition.kord.dao

import su.nnedition.kord.domain.Genre
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.domain.RandomSearchCriteria
import su.nnedition.kord.domain.User
import su.nnedition.kord.domain.UserCredential
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

class MediaFileDaoTest : DaoTestCase() {
    private val username = "media-file-dao-${UUID.randomUUID()}"
    private lateinit var folder: MusicFolder
    private lateinit var track: MediaFile
    private lateinit var originalGenres: List<Genre>

    @Before
    fun setUp() {
        originalGenres = daos().mediaFileDao.getGenres(false)
        val now = Instant.now()
        folder = MusicFolder(Paths.get("/media-file-dao-${UUID.randomUUID()}"), "DAO test", MusicFolder.Type.MEDIA, true, now)
        daos().musicFolderDao.createMusicFolder(folder)
        track = MediaFile(
            null, "dao-test/track.mp3", folder.id, MediaFile.MediaType.MUSIC, "mp3", "DAO test track",
            "DAO test album", "DAO test artist", "DAO test artist", 1, 1, 2024, "DAO test genre", 320,
            false, 180.0, 1024L, null, null, "dao-test", 0, null, "", now, now, now, now, true, MediaFileDao.VERSION,
            null, null
        )
        daos().mediaFileDao.createOrUpdateMediaFile(track) { }
    }

    @After
    fun tearDown() {
        track.id?.let { getJdbcHelper().update("delete from starred_media_file where media_file_id=?", it) }
        track.id?.let { getJdbcHelper().update("delete from media_file where id=?", it) }
        daos().mediaFileDao.updateGenres(originalGenres)
        daos().musicFolderDao.deleteMusicFolder(folder.id)
        getJdbcHelper().update("delete from user_credentials where username=?", username)
        getJdbcHelper().update("delete from users where username=?", username)
    }

    @Test
    fun `create update and read preserve media metadata`() {
        val id = assertNotNull(track.id)
        val loaded = assertNotNull(daos().mediaFileDao.getMediaFile(id))
        assertEquals("DAO test track", loaded.title)
        assertEquals(1, loaded.discNumber)
        assertEquals(folder.id, loaded.folderId)

        loaded.title = "Updated DAO test track"
        daos().mediaFileDao.createOrUpdateMediaFile(loaded) { error("Existing media file must not be inserted") }

        assertEquals("Updated DAO test track", daos().mediaFileDao.getMediaFile(id)?.title)
        assertNull(daos().mediaFileDao.getMediaFile("missing.mp3", assertNotNull(folder.id)))
    }

    @Test
    fun `genre aggregation and starred random-song filters remain available`() {
        assertTrue(daos().mediaFileDao.updateGenres(listOf(Genre("DAO test genre", 1, 1))))
        assertEquals(1, daos().mediaFileDao.getGenres(false).single { it.name == "DAO test genre" }.getSongCount())

        val user = User(username, null)
        daos().userDao.createUser(user, UserCredential(username, username, "secret", "noop", UserCredential.App.KORDINATOR))
        val criteria = RandomSearchCriteria(
            10, "DAO test genre", 2024, 2024, listOf(folder), null, null, null, null, null, null,
            true, false, null
        )
        assertTrue(daos().mediaFileDao.getRandomSongs(criteria, username).isEmpty())

        daos().mediaFileDao.starMediaFile(assertNotNull(track.id), username)
        assertEquals(track.id, daos().mediaFileDao.getStarredFiles(0, 10, username, listOf(folder)).single().id)
        assertEquals(track.id, daos().mediaFileDao.getRandomSongs(criteria, username).single().id)
        assertNotNull(daos().mediaFileDao.getMediaFileStarredDate(assertNotNull(track.id), username))

        daos().mediaFileDao.unstarMediaFile(assertNotNull(track.id), username)
        assertFalse(daos().mediaFileDao.getRandomSongs(criteria, username).isNotEmpty())
    }
}
