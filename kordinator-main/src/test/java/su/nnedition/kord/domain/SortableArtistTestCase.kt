package su.nnedition.kord.domain

import junit.framework.TestCase
import java.text.Collator
import java.util.Locale

class SortableArtistTestCase : TestCase() {
    private lateinit var collator: Collator

    override fun setUp() {
        collator = Collator.getInstance(Locale.US)
    }

    fun testSorting() {
        val artists = mutableListOf(
            TestSortableArtist("ABBA"), TestSortableArtist("Abba"), TestSortableArtist("abba"),
            TestSortableArtist("ACDC"), TestSortableArtist("acdc"), TestSortableArtist("ACDC"),
            TestSortableArtist("abc"), TestSortableArtist("ABC"),
        )

        artists.sort()
        assertEquals("[abba, Abba, ABBA, abc, ABC, acdc, ACDC, ACDC]", artists.toString())
    }

    fun testSortingWithAccents() {
        val a1 = TestSortableArtist("Sea")
        val a2 = TestSortableArtist("SEB")
        val a3 = TestSortableArtist("Seb")
        val a4 = TestSortableArtist("Séb")
        val a5 = TestSortableArtist("Sed")
        val a6 = TestSortableArtist("See")

        assertTrue(a1.compareTo(a1) == 0)
        assertTrue(a1.compareTo(a2) < 0)
        assertTrue(a1.compareTo(a3) < 0)
        assertTrue(a1.compareTo(a4) < 0)
        assertTrue(a1.compareTo(a5) < 0)
        assertTrue(a1.compareTo(a6) < 0)

        assertTrue(a2.compareTo(a1) > 0)
        assertTrue(a3.compareTo(a1) > 0)
        assertTrue(a4.compareTo(a1) > 0)
        assertTrue(a5.compareTo(a1) > 0)
        assertTrue(a6.compareTo(a1) > 0)

        assertTrue(a4.compareTo(a1) > 0)
        assertTrue(a4.compareTo(a2) > 0)
        assertTrue(a4.compareTo(a3) > 0)
        assertTrue(a4.compareTo(a4) == 0)
        assertTrue(a4.compareTo(a5) < 0)
        assertTrue(a4.compareTo(a6) < 0)

        val artists = mutableListOf(a1, a2, a3, a4, a5, a6)
        artists.shuffle()
        artists.sort()
        assertEquals("[Sea, Seb, SEB, Séb, Sed, See]", artists.toString())
    }

    fun testCollation() {
        val artists = mutableListOf(
            TestSortableArtist("péché"), TestSortableArtist("peach"), TestSortableArtist("pêche"),
        )

        artists.sort()
        assertEquals("[peach, péché, pêche]", artists.toString())
    }

    private inner class TestSortableArtist(sortableName: String) :
        MusicIndex.SortableArtist(sortableName, sortableName, collator) {
        override fun toString(): String = sortableName
    }
}
