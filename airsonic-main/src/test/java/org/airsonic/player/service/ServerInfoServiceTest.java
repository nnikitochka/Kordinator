package org.airsonic.player.service;

import org.airsonic.player.domain.Version;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ServerInfoServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private final VersionService versions = mock(VersionService.class);
    private final SettingsService settings = mock(SettingsService.class);
    private final ServerInfoService service = new ServerInfoService(versions, settings);

    @Test public void snapshotUsesExplicitValuesAndPrefersFinalReleaseWithoutReadingTheLog() {
        when(settings.getBrand()).thenReturn("Музыка <img>");
        when(versions.getLocalVersion()).thenReturn(new Version("11.0"));
        when(versions.getLocalBuildDate()).thenReturn(Instant.parse("2026-11-09T00:00:00Z"));
        when(versions.getLocalBuildNumber()).thenReturn("abcdef1234567");
        when(versions.isNewFinalVersionAvailable()).thenReturn(true);
        when(versions.getLatestFinalVersion()).thenReturn(release("12.0", "https://example.test/final"));
        var data = service.snapshot("Test servlet");
        assertEquals("Музыка <img>", data.get("brand"));
        assertEquals("11.0", data.get("localVersion"));
        assertEquals("2026-11-09T00:00:00Z", data.get("buildDate"));
        assertEquals("abcdef1234567", data.get("buildNumber"));
        assertEquals("12.0", data.get("latestVersion"));
        assertEquals("https://example.test/final", data.get("latestVersionUrl"));
        assertEquals(true, data.get("newVersionAvailable"));
        assertTrue(data.get("serverInfo").toString().startsWith("Test servlet, java "));
        assertTrue(((Long) data.get("totalMemory")) > 0);
        assertTrue(((Long) data.get("usedMemory")) >= 0);
        assertFalse(data.containsKey("logFile"));
        assertFalse(data.containsKey("user"));
        verify(versions, never()).isNewBetaVersionAvailable();
        verify(settings, never()).getLogFile();
    }

    @Test public void supportsBetaAndUnknownBuildMetadata() {
        when(versions.isNewBetaVersionAvailable()).thenReturn(true);
        when(versions.getLatestBetaVersion()).thenReturn(release("12.0-beta", "https://example.test/beta"));
        var data = service.snapshot("Test");
        assertEquals("12.0-beta", data.get("latestVersion"));
        assertNull(data.get("localVersion"));
        assertNull(data.get("buildDate"));
        when(versions.isNewBetaVersionAvailable()).thenReturn(false);
        assertEquals(false, service.snapshot("Test").get("newVersionAvailable"));
    }

    @Test public void returnsLastFiftyUnicodeLinesInChronologicalOrder() throws Exception {
        var path = temp.newFile("server.log").toPath();
        List<String> lines = IntStream.range(0, 75).mapToObj(i -> "Строка " + i + " <script>&").toList();
        Files.writeString(path, String.join("\r\n", lines) + "\r\n");
        when(settings.getLogFile()).thenReturn(path.toString());
        var log = service.log();
        assertEquals(true, log.get("available"));
        assertEquals(path.toString(), log.get("logFile"));
        assertEquals(lines.subList(25, 75), log.get("logEntries"));
        Files.writeString(path, "");
        assertEquals(List.of(), service.log().get("logEntries"));
        Files.writeString(path, "одна строка без перевода");
        assertEquals(List.of("одна строка без перевода"), service.log().get("logEntries"));
    }

    @Test public void missingLogDoesNotPreventReadingServerInformation() {
        when(settings.getLogFile()).thenReturn(temp.getRoot().toPath().resolve("missing.log").toString());
        var data = service.log();
        assertEquals(false, data.get("available"));
        assertEquals(List.of(), data.get("logEntries"));
        assertNotNull(service.snapshot("Test").get("serverInfo"));
    }

    private Version release(String number, String url) {
        return new Version(number, "commit", false, url, null, null, List.of(Map.of("name", "private-release-details")));
    }
}
