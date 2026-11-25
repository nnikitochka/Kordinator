package su.nnedition.kord.dao

import su.nnedition.kord.domain.SavedPlayQueue
import org.junit.After
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlayQueueDaoTest : DaoTestCase() {
    @After
    fun tearDown() {
        getJdbcHelper().update("delete from play_queue where username=?", USERNAME)
    }

    @Test
    fun `saved queue replacement preserves current track metadata`() {
        val dao = daos().playQueueDao
        val changed = Instant.now()
        val queue = SavedPlayQueue(null, USERNAME, emptyList(), 17, 250L, changed, "desktop")

        dao.savePlayQueue(queue)
        val firstId = assertNotNull(queue.id)
        val saved = assertNotNull(dao.getPlayQueue(USERNAME))
        assertEquals(firstId, saved.id)
        assertEquals(emptyList(), saved.mediaFileIds)
        assertEquals(17, saved.currentMediaFileId)
        assertEquals(250L, saved.positionMillis)
        assertEquals(changed, saved.changed)
        assertEquals("desktop", saved.changedBy)

        val replacement = SavedPlayQueue(null, USERNAME, emptyList(), 42, 900L, changed.plusSeconds(1), "mobile")
        dao.savePlayQueue(replacement)
        val replaced = assertNotNull(dao.getPlayQueue(USERNAME))
        assertEquals(replacement.id, replaced.id)
        assertEquals(42, replaced.currentMediaFileId)
        assertEquals(900L, replaced.positionMillis)
        assertEquals("mobile", replaced.changedBy)
    }

    private companion object {
        private const val USERNAME = "admin"
    }
}
