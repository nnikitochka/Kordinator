package su.nnedition.kord.domain

import junit.framework.TestCase

class MediaFileComparatorTestCase : TestCase() {
    private val comparator = MediaFileComparator(true)

    fun testCompareAlbums() {
        val albumA2012 = album("a", 2012)
        val albumB2012 = album("b", 2012)
        val album2013 = album("c", 2013)
        val albumWithoutYear = album("c")

        assertEquals(0, comparator.compare(albumWithoutYear, albumWithoutYear))
        assertEquals(0, comparator.compare(albumA2012, albumA2012))
        assertEquals(-1, comparator.compare(albumA2012, albumWithoutYear))
        assertEquals(-1, comparator.compare(album2013, albumWithoutYear))
        assertEquals(1, comparator.compare(album2013, albumA2012))
        assertEquals(1, comparator.compare(albumWithoutYear, albumA2012))
        assertEquals(1, comparator.compare(albumWithoutYear, album2013))
        assertEquals(-1, comparator.compare(albumA2012, album2013))
        assertEquals(-1, comparator.compare(albumA2012, albumB2012))
        assertEquals(1, comparator.compare(albumB2012, albumA2012))
    }

    fun testCompareDiscNumbers() {
        val discXtrack1 = track(trackNumber = 1)
        val discXtrack2 = track(trackNumber = 2)
        val disc5track1 = track(discNumber = 5, trackNumber = 1)
        val disc5track2 = track(discNumber = 5, trackNumber = 2)
        val disc6track1 = track(discNumber = 6, trackNumber = 1)
        val disc6track2 = track(discNumber = 6, trackNumber = 2)

        assertEquals(0, comparator.compare(discXtrack1, discXtrack1))
        assertEquals(0, comparator.compare(disc5track1, disc5track1))
        assertEquals(-1, comparator.compare(discXtrack1, discXtrack2))
        assertEquals(1, comparator.compare(discXtrack2, discXtrack1))
        assertEquals(-1, comparator.compare(disc5track1, disc5track2))
        assertEquals(1, comparator.compare(disc6track2, disc5track1))
        assertEquals(-1, comparator.compare(disc5track1, disc6track1))
        assertEquals(1, comparator.compare(disc6track1, disc5track1))
        assertEquals(-1, comparator.compare(disc5track2, disc6track1))
        assertEquals(1, comparator.compare(disc6track1, disc5track2))
        assertEquals(-1, comparator.compare(discXtrack1, disc5track1))
        assertEquals(1, comparator.compare(disc5track1, discXtrack1))
        assertEquals(-1, comparator.compare(discXtrack1, disc5track2))
        assertEquals(1, comparator.compare(disc5track2, discXtrack1))
    }

    private fun album(path: String, year: Int? = null) = MediaFile().apply {
        mediaType = MediaFile.MediaType.ALBUM
        this.path = path
        this.year = year
    }

    private fun track(discNumber: Int? = null, trackNumber: Int) = MediaFile().apply {
        mediaType = MediaFile.MediaType.MUSIC
        path = "a"
        this.discNumber = discNumber
        this.trackNumber = trackNumber
    }
}
