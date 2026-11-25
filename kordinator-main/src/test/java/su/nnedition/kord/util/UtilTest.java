package su.nnedition.kord.util;

import su.nnedition.kord.domain.MediaLibraryStatistics;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class UtilTest {

    @Test
    public void anonymizedUrlReplacesSensitiveValuesAndPreservesOtherQueryData() {
        assertEquals("https://example.test/path?x=keep&p=<hidden>&t=<hidden>&s=<hidden>&u=<hidden>",
                Util.anonymizeUrl("https://example.test/path?p=secret&t=token&s=salt&u=user&x=keep"));
    }

    @Test
    public void anonymizedUrlPreservesEncodedValuesAndReservedCharacters() {
        assertEquals("https://example.test/a%20b?x=a%2Bb%2Fc%3Dd%26e%3Df&empty=&p=<hidden>",
                Util.anonymizeUrl("https://example.test/a%20b?p=a%2Fb&x=a%2Bb%2Fc%3Dd%26e%3Df&empty="));
    }

    @Test
    public void anonymizedUrlReplacesEveryDuplicateSensitiveValue() {
        assertEquals("https://example.test/path?x=1&x=2&p=<hidden>",
                Util.anonymizeUrl("https://example.test/path?p=one&p=two&x=1&x=2"));
    }

    @Test
    public void anonymizedUrlWithoutQueryHasNoQuestionMark() {
        assertEquals("https://example.test/path", Util.anonymizeUrl("https://example.test/path"));
        assertEquals("https://example.test/path", Util.anonymizeUrl("https://example.test/path?"));
    }

    @Test
    public void anonymizedUrlMalformedQueryValueIsPreservedUnlessSensitive() {
        assertEquals("https://example.test/path?x=%ZZ&p=<hidden>",
                Util.anonymizeUrl("https://example.test/path?x=%ZZ&p=%ZZ"));
    }

    @Test
    public void anonymizedUrlOnlyMatchesLiteralSensitiveParameterNames() {
        assertEquals("https://example.test/path?%70=encoded-key&p=<hidden>",
                Util.anonymizeUrl("https://example.test/path?%70=encoded-key&p=value"));
    }

    @Test
    public void anonymizedUrlMalformedAuthorityIsNormalized() {
        assertEquals("http://[]", Util.anonymizeUrl("http://[::1"));
    }

    @Test
    public void objectToStringMapNull() {
        MediaLibraryStatistics statistics = null;
        Map<String, String> stringStringMap = Util.objectToStringMap(statistics);
        assertNull(stringStringMap);
    }

    @Test
    public void objectToStringMap() {
        MediaLibraryStatistics statistics = new MediaLibraryStatistics();
        statistics.incrementAlbums(5);
        statistics.incrementSongs(4);
        statistics.incrementArtists(910823);
        statistics.incrementTotalDurationInSeconds(30);
        statistics.incrementTotalLengthInBytes(2930491082L);
        Map<String, String> stringStringMap = Util.objectToStringMap(statistics);
        assertEquals("5", stringStringMap.get("albumCount"));
        assertEquals("4", stringStringMap.get("songCount"));
        assertEquals("910823", stringStringMap.get("artistCount"));
        assertEquals("30.0", stringStringMap.get("totalDurationInSeconds"));
        assertEquals("2930491082", stringStringMap.get("totalLengthInBytes"));
        assertEquals(statistics.getScanDate().getEpochSecond() + "." + StringUtils.leftPad(String.valueOf(statistics.getScanDate().getNano()), 9, "0"), stringStringMap.get("scanDate"));
    }

    @Test
    public void stringMapToObject() {
        Map<String, String> stringStringMap = new HashMap<>();
        stringStringMap.put("albumCount", "5");
        stringStringMap.put("songCount", "4");
        stringStringMap.put("artistCount", "910823");
        stringStringMap.put("totalDurationInSeconds", "30");
        stringStringMap.put("totalLengthInBytes", "2930491082");
        stringStringMap.put("scanDate", "1568350960.725");
        MediaLibraryStatistics statistics = Util.stringMapToObject(MediaLibraryStatistics.class, stringStringMap);
        assertEquals(5, statistics.getAlbumCount());
        assertEquals(4, statistics.getSongCount());
        assertEquals(910823, statistics.getArtistCount());
        assertEquals(30.0, statistics.getTotalDurationInSeconds(), 0.0001);
        assertEquals(2930491082L, statistics.getTotalLengthInBytes());
        assertEquals(Instant.ofEpochMilli(1568350960725L), statistics.getScanDate());
    }

    @Test
    public void stringMapToObjectWithExtraneousData() {
        Map<String, String> stringStringMap = new HashMap<>();
        stringStringMap.put("albumCount", "5");
        stringStringMap.put("songCount", "4");
        stringStringMap.put("artistCount", "910823");
        stringStringMap.put("totalDurationInSeconds", "30");
        stringStringMap.put("totalLengthInBytes", "2930491082");
        stringStringMap.put("scanDate", "1568350960.725");
        stringStringMap.put("extraneousData", "nothingHereToLookAt");
        MediaLibraryStatistics statistics = Util.stringMapToObject(MediaLibraryStatistics.class, stringStringMap);
        assertEquals(5, statistics.getAlbumCount());
        assertEquals(4, statistics.getSongCount());
        assertEquals(910823, statistics.getArtistCount());
        assertEquals(30.0, statistics.getTotalDurationInSeconds(), 0.0001);
        assertEquals(2930491082L, statistics.getTotalLengthInBytes());
        assertEquals(Instant.ofEpochMilli(1568350960725L), statistics.getScanDate());
    }

    @Test
    public void stringMapToObjectWithNoData() {
        Map<String, String> stringStringMap = new HashMap<>();
        MediaLibraryStatistics statistics = Util.stringMapToObject(MediaLibraryStatistics.class, stringStringMap);
        assertNotNull(statistics);
    }

}
