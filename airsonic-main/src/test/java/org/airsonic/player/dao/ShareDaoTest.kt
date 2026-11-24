package org.airsonic.player.dao

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.Share
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ShareDaoTest : DaoTestCase() {
    private lateinit var share: Share
    private lateinit var folder: MusicFolder
    private lateinit var mediaFile: MediaFile

    @Before
    fun setUp() {
        share = Share().apply {
            name = "dao-test-${UUID.randomUUID()}"
            description = "Initial description"
            username = USERNAME
            created = Instant.now()
            expires = created!!.plusSeconds(3600)
            visitCount = 2
            daos().shareDao.createShare(this)
        }
        val now = Instant.now()
        folder = MusicFolder(Paths.get("/share-dao-${UUID.randomUUID()}"), "Share DAO test", MusicFolder.Type.MEDIA, true, now)
        daos().musicFolderDao.createMusicFolder(folder)
        mediaFile = MediaFile(
            null, "share-dao/track.mp3", folder.id, MediaFile.MediaType.MUSIC, "mp3", "Share DAO track",
            "Share DAO album", "Share DAO artist", "Share DAO artist", 1, 1, 2024, "Share DAO genre", 320,
            false, 180.0, 1024L, null, null, "share-dao", 0, null, "", now, now, now, now, true,
            MediaFileDao.VERSION, null, null
        )
        daos().mediaFileDao.createOrUpdateMediaFile(mediaFile) { }
    }

    @After
    fun tearDown() {
        if (::share.isInitialized) daos().shareDao.deleteShare(share.id)
        if (::mediaFile.isInitialized) mediaFile.id?.let { getJdbcHelper().update("delete from media_file where id=?", it) }
        if (::folder.isInitialized) daos().musicFolderDao.deleteMusicFolder(folder.id)
    }

    @Test
    fun `share insert read update and delete preserve values`() {
        val dao = daos().shareDao
        val loaded = assertNotNull(dao.getShareById(share.id))
        assertEquals(share.name, loaded.name)
        assertEquals(share.name, dao.getShareByName(share.name!!)?.name)
        assertEquals(share.id, dao.getAllShares().single { it.id == share.id }.id)
        assertEquals(share.created, loaded.created)
        assertEquals(share.expires, loaded.expires)

        loaded.description = "Updated description"
        loaded.lastVisited = Instant.now()
        loaded.visitCount = 3
        dao.updateShare(loaded)

        val updated = assertNotNull(dao.getShareById(share.id))
        assertEquals("Updated description", updated.description)
        assertEquals(loaded.lastVisited, updated.lastVisited)
        assertEquals(3, updated.visitCount)

        dao.deleteShare(share.id)
        assertNull(dao.getShareById(share.id))
    }

    @Test
    fun `share file batches preserve folder-scoped lookup`() {
        val dao = daos().shareDao
        dao.createSharedFiles(share.id, null)
        dao.createSharedFiles(share.id, emptyList())
        assertEquals(emptyList(), dao.getSharedFiles(share.id, emptyList()))

        val mediaFileId = assertNotNull(mediaFile.id)
        dao.createSharedFiles(share.id, listOf(mediaFileId))
        assertEquals(listOf(mediaFileId), dao.getSharedFiles(share.id, listOf(folder)))
    }

    private companion object {
        const val USERNAME = "admin"
    }
}
