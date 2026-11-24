package org.airsonic.player.dao

import org.airsonic.player.domain.Playlist
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlaylistDaoTest : DaoTestCase() {
    private lateinit var first: Playlist
    private lateinit var second: Playlist

    @Before
    fun setUp() {
        first = createPlaylist("B playlist")
        second = createPlaylist("A playlist")
    }

    @After
    fun tearDown() {
        if (::first.isInitialized) daos().playlistDao.deletePlaylist(first.id)
        if (::second.isInitialized) daos().playlistDao.deletePlaylist(second.id)
    }

    @Test
    fun `queries sort playlists and mutations retain playlist metadata`() {
        val dao = daos().playlistDao
        assertEquals(listOf(second.id, first.id), dao.getWritablePlaylistsForUser(USERNAME).map { it.id })
        assertEquals(listOf(second.id, first.id), dao.getAllPlaylists().map { it.id })
        assertEquals("B playlist", assertNotNull(dao.getPlaylist(first.id)).name)
        assertTrue(dao.getReadablePlaylistsForUser(USERNAME).any { it.id == first.id })

        dao.setFilesInPlaylist(first.id, emptyList())
        assertEquals(0, dao.getPlaylistFileStats(first.id)!!.left)
        dao.addPlaylistUser(first.id, USERNAME)
        dao.addPlaylistUser(first.id, USERNAME)
        assertEquals(listOf(USERNAME), dao.getPlaylistUsers(first.id))

        first.name = "Updated playlist"
        first.shared = true
        dao.updatePlaylist(first)
        val updated = assertNotNull(dao.getPlaylist(first.id))
        assertEquals("Updated playlist", updated.name)
        assertTrue(updated.shared)
        dao.deletePlaylistUser(first.id, USERNAME)
        assertTrue(dao.getPlaylistUsers(first.id).isEmpty())
    }

    private fun createPlaylist(name: String): Playlist = Playlist().apply {
        username = USERNAME
        this.name = name
        created = java.time.Instant.now()
        changed = created
        daos().playlistDao.createPlaylist(this)
    }

    private companion object {
        private const val USERNAME = "admin"
    }
}
