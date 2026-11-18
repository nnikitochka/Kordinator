package org.airsonic.player.service.hls;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.security.HlsLinkContext;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.SettingsService;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HlsPlaylistServiceUrlTest {

    private static final String BASE_URL = "https://media.example.test/airsonic/";

    @Test
    public void variantUrlsPreservePathQueryOrderingAndSignedClaims() {
        Fixture fixture = new Fixture();

        List<String> urls = fixture.playlists.generate(fixture.caller, fixture.media, fixture.player,
                BASE_URL, "ext/rest-hls", new String[]{"500@321x241", "1000@640x480"}, null, true)
                .lines().filter(line -> !line.startsWith("#") && !line.isBlank()).collect(Collectors.toList());

        assertEquals(2, urls.size());
        assertSignedUrl(urls.get(0), "ext/rest-hls/hls.m3u8?id=42&player=7&maxBitRate=500@320x240", fixture.expirationStart);
        assertSignedUrl(urls.get(1), "ext/rest-hls/hls.m3u8?id=42&player=7&maxBitRate=1000@640x480", fixture.expirationStart);
    }

    @Test
    public void normalSegmentUrlsPreserveSegmentAndSizingQueryOrder() {
        Fixture fixture = new Fixture();

        List<String> urls = fixture.playlists.generate(fixture.caller, fixture.media, fixture.player,
                BASE_URL, "ext/rest-hls", new String[]{"500@321x241"}, null, true)
                .lines().filter(line -> !line.startsWith("#") && !line.isBlank()).collect(Collectors.toList());

        assertEquals(3, urls.size());
        assertSignedUrl(urls.get(0), "ext/rest-hls/segment/segment.ts?id=42&hls=true&segmentIndex=0&player=7&duration=10&maxBitRate=500&size=320x240", fixture.expirationStart);
        assertSignedUrl(urls.get(1), "ext/rest-hls/segment/segment.ts?id=42&hls=true&segmentIndex=1&player=7&duration=10&maxBitRate=500&size=320x240", fixture.expirationStart);
        assertSignedUrl(urls.get(2), "ext/rest-hls/segment/segment.ts?id=42&hls=true&segmentIndex=2&player=7&duration=10&maxBitRate=500&size=320x240", fixture.expirationStart);
    }

    private void assertSignedUrl(String url, String claimPath, Instant createdAt) {
        assertTrue(url.startsWith(BASE_URL + claimPath + "&jwt="));
        String token = url.substring(url.indexOf("&jwt=") + "&jwt=".length());
        DecodedJWT decoded = JWTSecurityService.decode(token);
        assertEquals("listener", decoded.getSubject());
        assertEquals(claimPath, decoded.getClaim(JWTSecurityService.CLAIM_PATH).asString());
        assertTrue(decoded.getExpiresAt().toInstant().isAfter(createdAt.plusSeconds(3600)));
        assertTrue(decoded.getExpiresAt().toInstant().isBefore(createdAt.plusSeconds(3622)));
    }

    private static final class Fixture {
        private final Instant expirationStart = Instant.now();
        private final SettingsService settings = mock(SettingsService.class);
        private final HlsLinkContext caller = new HlsLinkContext("listener", false, null);
        private final MediaFile media = mock(MediaFile.class);
        private final Player player = mock(Player.class);
        private final HlsPlaylistService playlists;

        private Fixture() {
            when(settings.getJWTKey()).thenReturn("test-jwt-key");
            when(media.getId()).thenReturn(42);
            when(media.getDuration()).thenReturn(21.0);
            when(media.getWidth()).thenReturn(1280);
            when(media.getHeight()).thenReturn(720);
            when(player.getId()).thenReturn(7);
            playlists = new HlsPlaylistService(new JWTSecurityService(settings));
        }
    }
}
