package org.airsonic.player.dao

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AvatarDaoTest : DaoTestCase() {
    @Test
    fun `reads seeded system avatars through list and id lookups`() {
        val dao = daos().avatarDao
        val avatars = dao.getAllSystemAvatars()

        assertTrue(avatars.isNotEmpty())
        val first = avatars.first()
        val byId = assertNotNull(dao.getSystemAvatar(first.id))
        assertEquals(first.name, byId.name)
        assertEquals(first.mimeType, byId.mimeType)
        assertEquals(first.path, byId.path)
    }
}
