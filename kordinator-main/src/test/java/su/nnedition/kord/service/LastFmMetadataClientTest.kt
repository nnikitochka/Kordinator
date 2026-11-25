package su.nnedition.kord.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LastFmMetadataClientTest {
    private val client = LastFmMetadataClient()

    @Test
    fun `wiki markup opens links in a new tab and strips attribution`() {
        val result = client.processWikiText(
            "  Bio <a href=\"https://last.fm/artist\">Artist</a>\n" +
                "User-contributed text.*  "
        )

        assertEquals("Bio <a target='_blank' href=\"https://last.fm/artist\">Artist</a>", result)
    }

    @Test
    fun `incorrect tag biography is suppressed`() {
        assertNull(client.processWikiText("This is an incorrect tag for this artist."))
    }

    @Test
    fun `redirect artist is extracted from Last fm warning`() {
        val biography = client.processWikiText(
            "This is mistagged for <a target='_blank' href=\"https://last.fm/music/Artist\" " +
                "class=\"bbcode_artist\">Canonical Artist</a>"
        )

        assertEquals("Canonical Artist", client.redirectedArtist(biography))
    }

    @Test
    fun `biography without redirect returns no replacement`() {
        assertNull(client.redirectedArtist("An ordinary biography without a correction."))
    }
}
