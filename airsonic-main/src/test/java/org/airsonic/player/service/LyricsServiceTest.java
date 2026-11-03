package org.airsonic.player.service;

import org.apache.http.client.HttpResponseException;
import org.apache.http.conn.ConnectTimeoutException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class LyricsServiceTest {
    private static final String FOUND = """
            <GetLyricResult xmlns="http://api.chartlyrics.com/">
              <LyricArtist>Artist &amp; Co</LyricArtist><LyricSong>Title</LyricSong>
              <Lyric>  Первая строка
            &lt;script&gt;literal&lt;/script&gt;  </Lyric>
            </GetLyricResult>
            """;

    @Test
    void encodesSearchAndParsesNamespacedLyricsAsText() {
        LyricsService service = new LyricsService(url -> {
            assertTrue(url.endsWith("artist=Artist+%26+Co&song=%D0%9F%D0%B5%D1%81%D0%BD%D1%8F+%3F"), url);
            return FOUND;
        });
        LyricsService.Result result = service.getLyrics("Artist & Co", "Песня ?");
        assertEquals("Artist & Co", result.artist());
        assertEquals("Title", result.title());
        assertEquals("Первая строка\n<script>literal</script>", result.lyrics());
        assertFalse(result.tryLater());
    }

    @Test
    void missingSearchParametersDoNotContactTheProvider() {
        LyricsService service = new LyricsService(url -> { fail("Unexpected provider call"); return null; });
        assertEquals(LyricsService.Result.empty(false), service.getLyrics(null, "Title"));
        assertEquals(LyricsService.Result.empty(false), service.getLyrics("Artist", null));
    }

    @Test
    void emptySearchStringsArePassedToTheProviderAsBefore() {
        LyricsService service = new LyricsService(url -> {
            assertTrue(url.endsWith("artist=&song=Title"), url);
            return FOUND;
        });
        assertNotNull(service.getLyrics("", "Title").lyrics());
    }

    @Test
    void whitespaceLyricsMeanNotFound() {
        LyricsService service = new LyricsService(url -> "<GetLyricResult><Lyric> \n </Lyric></GetLyricResult>");
        assertNull(service.getLyrics("Artist", "Title").lyrics());
        assertFalse(service.getLyrics("Artist", "Title").tryLater());
    }

    @Test
    void unavailableProviderAndNetworkTimeoutsAllowRetry() {
        for (IOException failure : new IOException[]{new HttpResponseException(503, "Unavailable"),
                new SocketException("reset"), new SocketTimeoutException("read timed out"),
                new ConnectTimeoutException("connect timed out")}) {
            LyricsService service = new LyricsService(url -> { throw failure; });
            assertEquals(LyricsService.Result.empty(true), service.getLyrics("Artist", "Title"));
        }
    }

    @Test
    void otherFailuresKeepTheExistingEmptyResultContract() {
        LyricsService service = new LyricsService(url -> { throw new HttpResponseException(404, "Not found"); });
        assertEquals(LyricsService.Result.empty(false), service.getLyrics("Artist", "Title"));
        service = new LyricsService(url -> "<invalid");
        assertEquals(LyricsService.Result.empty(false), service.getLyrics("Artist", "Title"));
    }

    @Test
    void providerXmlCannotResolveExternalEntities() {
        LyricsService service = new LyricsService(url -> """
                <!DOCTYPE lyric [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <GetLyricResult><Lyric>&secret;</Lyric></GetLyricResult>
                """);
        assertNull(service.getLyrics("Artist", "Title").lyrics());
    }
}
