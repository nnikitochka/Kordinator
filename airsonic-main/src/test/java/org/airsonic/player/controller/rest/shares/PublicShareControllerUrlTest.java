package org.airsonic.player.controller.rest.shares;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.ShareService;
import org.airsonic.player.service.VideoPlaybackService;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.junit.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PublicShareControllerUrlTest {
    private static final String BASE_URL = "https://media.example.test:8443/airsonic/";
    private static final Instant EXPIRATION = Instant.parse("2031-04-05T06:07:08Z");

    @Test
    public void directAudioStreamCaptionAndCoverUrlsPreserveQueryOrderAndClaims() throws Exception {
        Fixture fixture = new Fixture();
        MediaFile file = audioFile(31, 7, "song.mp3");
        Map<String, Object> media = fixture.media(file, EXPIRATION);

        String stream = (String) media.get("streamUrl");
        String cover = (String) media.get("coverArtUrl");
        String captions = (String) media.get("captionsUrl");
        assertSignedUrl(stream, "ext/stream?id=31&player=17&format=raw", User.USERNAME_GUEST, EXPIRATION);
        assertSignedUrl(cover, "ext/coverArt.view?id=31&size=500", User.USERNAME_GUEST, EXPIRATION);
        assertSignedUrl(captions, "ext/rest/captions/list?id=31", User.USERNAME_GUEST, EXPIRATION);
    }

    @Test
    public void hlsUrlKeepsBitrateValueAndQueryOrderInSignedClaim() throws Exception {
        Fixture fixture = new Fixture();
        MediaFile file = new MediaFile();
        file.setId(32);
        file.setFolderId(7);
        file.setPath("clip.mkv");
        file.setFormat("mkv");
        file.setMediaType(MediaFile.MediaType.VIDEO);
        file.setTitle("Clip");
        Map<String, Object> media = fixture.media(file, EXPIRATION);

        String stream = (String) media.get("streamUrl");
        assertEquals("application/x-mpegurl", media.get("contentType"));
        assertTrue(stream.startsWith(BASE_URL + "ext/hls/hls.m3u8?id=32&player=17&maxBitRate="));
        String signedPath = JWTSecurityService.decode(tokenFrom(stream, "&jwt=").getToken())
                .getClaim(JWTSecurityService.CLAIM_PATH).asString();
        String bitrateParameters = VideoPlaybackService.BIT_RATES.stream()
                .map(rate -> "&maxBitRate=" + rate)
                .collect(java.util.stream.Collectors.joining());
        assertEquals("ext/hls/hls.m3u8?id=32&player=17" + bitrateParameters, signedPath);
        assertEquals(EXPIRATION, tokenFrom(stream, "&jwt=").getExpiresAt().toInstant());
        assertTrue(stream.startsWith(BASE_URL + "ext/hls/hls.m3u8?id=32&player=17" + bitrateParameters + "&jwt="));
    }

    private void assertSignedUrl(String url, String path, String user, Instant expiration) {
        assertTrue(url.startsWith(BASE_URL + path + "&jwt="));
        DecodedJWT token = tokenFrom(url, "&jwt=");
        assertEquals(user, token.getSubject());
        assertEquals(path, token.getClaim(JWTSecurityService.CLAIM_PATH).asString());
        assertEquals(expiration, token.getExpiresAt().toInstant());
    }

    private DecodedJWT tokenFrom(String url, String marker) {
        return JWTSecurityService.decode(url.substring(url.indexOf(marker) + marker.length()));
    }

    private MediaFile audioFile(int id, int folderId, String path) {
        MediaFile file = new MediaFile();
        file.setId(id);
        file.setFolderId(folderId);
        file.setPath(path);
        file.setFormat("mp3");
        file.setMediaType(MediaFile.MediaType.MUSIC);
        file.setTitle(path);
        return file;
    }

    private static final class Fixture {
        private final ShareService shares = mock(ShareService.class);
        private final MediaFileService mediaFiles = mock(MediaFileService.class);
        private final MediaFolderService folders = mock(MediaFolderService.class);
        private final SettingsService settings = mock(SettingsService.class);
        private final JWTSecurityService jwt;
        private final PublicShareController controller;

        private Fixture() {
            when(settings.getJWTKey()).thenReturn("test-jwt-key");
            jwt = new JWTSecurityService(settings);
            controller = new PublicShareController(shares, mediaFiles, folders, jwt, mock(MetaDataParserFactory.class), new com.fasterxml.jackson.databind.ObjectMapper());
        }

        private PublicShareController.Request request(String name, Instant expiration) {
            Player player = new Player();
            player.setId(17);
            player.setUsername(User.USERNAME_GUEST);
            return new PublicShareController.Request(name, player, BASE_URL, expiration);
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> media(MediaFile file, Instant expiration) throws Exception {
            Method method = PublicShareController.class.getDeclaredMethod("media", MediaFile.class,
                    PublicShareController.Request.class, Instant.class);
            method.setAccessible(true);
            return (Map<String, Object>) method.invoke(controller, file, request("ShareName", expiration), expiration);
        }
    }
}
