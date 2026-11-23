package org.airsonic.player.domain

import org.junit.jupiter.api.Test
import java.io.ObjectStreamClass
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MusicIndexTest {
    @Test
    fun equalityUsesTheIndexAndPrefixesRemainMutable() {
        val index = MusicIndex("A")
        index.addPrefix("A")
        index.prefixes.add("Å")

        assertEquals(MusicIndex("A"), index)
        assertNotEquals(MusicIndex("B"), index)
        assertEquals(listOf("A", "Å"), index.prefixes)
        assertEquals("#", MusicIndex.OTHER.index)
    }

    @Test
    fun serializedClassIdentifierMatchesThePreviousJavaModel() {
        assertEquals(7631094417730994210L, ObjectStreamClass.lookup(MusicIndex::class.java).serialVersionUID)
    }
}
