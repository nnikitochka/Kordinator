package su.nnedition.kord.dao

import su.nnedition.kord.domain.MediaFile
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RatingDaoTest : DaoTestCase() {
    @Test
    fun `empty scopes and invalid ratings retain DAO guard behavior`() {
        val dao = daos().ratingDao
        val mediaFile = MediaFile()

        dao.setRatingForUser("admin", mediaFile, 0)
        dao.setRatingForUser("admin", mediaFile, 6)
        assertEquals(emptyList(), dao.getHighestRatedAlbums(0, 10, emptyList()))
        assertEquals(emptyList(), dao.getHighestRatedAlbums(0, 0, emptyList()))
        assertEquals(0, dao.getRatedAlbumCount("admin", emptyList()))
        assertNull(dao.getAverageRating(mediaFile))
        assertNull(dao.getRatingForUser("admin", mediaFile))
    }
}
