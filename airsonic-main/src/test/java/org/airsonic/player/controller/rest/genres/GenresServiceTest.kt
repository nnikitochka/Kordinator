package org.airsonic.player.controller.rest.genres

import org.airsonic.player.domain.Genre
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenresServiceTest {
    @Test
    fun `preserves genre names counts and lookup order`() {
        val service = GenresService {
            listOf(Genre("Рок & Jazz", 3, 2), Genre("Ambient", 7, 1))
        }

        val genres = service.getGenres().genre

        assertEquals(listOf("Рок & Jazz", "Ambient"), genres.map { it.content })
        assertEquals(listOf(3, 7), genres.map { it.songCount })
        assertEquals(listOf(2, 1), genres.map { it.albumCount })
    }

    @Test
    fun `returns an empty genre list for an empty library`() {
        assertTrue(GenresService { emptyList() }.getGenres().genre.isEmpty())
    }
}
