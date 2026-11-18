package org.airsonic.player.controller;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.security.HlsLinkContext;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.hls.HlsPlaylistService;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class HlsPlaylistServiceTest {
    @Test
    public void signsSegmentAndVariantUrlsForJwtPlayback() {
        JWTSecurityService jwt = mock(JWTSecurityService.class);
        when(jwt.addJWTToken(anyString(), anyString(), isNull(java.time.Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(1) + "&token=signed");
        HlsLinkContext caller = new HlsLinkContext("listener", true, null);
        MediaFile media = mock(MediaFile.class);
        when(media.getId()).thenReturn(42);
        when(media.getDuration()).thenReturn(21.0);
        Player player = mock(Player.class);
        when(player.getId()).thenReturn(7);
        HlsPlaylistService playlists = new HlsPlaylistService(jwt);

        String normal = playlists.generate(caller, media, player, "https://example.test/", "hls",
                new String[] {"500@321x241"}, null);
        assertTrue(normal, normal.contains("#EXTINF:10,"));
        assertTrue(normal, normal.contains("#EXTINF:1,"));
        assertTrue(normal, normal.contains("size=320x240&token=signed"));
        verify(jwt, times(3)).addJWTToken(eq("listener"), anyString(), isNull(java.time.Instant.class));

        String variants = playlists.generate(caller, media, player, "https://example.test/", "hls",
                new String[] {"500@320x240", "1000@640x480"}, null);
        assertTrue(variants, variants.contains("maxBitRate=500@320x240&token=signed"));
        assertTrue(variants, variants.contains("maxBitRate=1000@640x480&token=signed"));
        verify(jwt, times(5)).addJWTToken(eq("listener"), anyString(), isNull(java.time.Instant.class));
    }
    @Test
    public void restLinksRemainValidForLongVideos() {
        JWTSecurityService jwt = mock(JWTSecurityService.class);
        when(jwt.addJWTToken(anyString(), anyString(), any(java.time.Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        HlsLinkContext caller = new HlsLinkContext("listener", false, null);
        MediaFile media = mock(MediaFile.class);
        when(media.getId()).thenReturn(42);
        when(media.getDuration()).thenReturn(7200.0);
        Player player = mock(Player.class);
        when(player.getId()).thenReturn(7);
        new HlsPlaylistService(jwt).generate(caller, media, player, "https://example.test/", "ext/rest-hls",
                new String[] {"500@320x240", "1000@640x480"}, null, true);
        org.mockito.ArgumentCaptor<java.time.Instant> expiry = org.mockito.ArgumentCaptor.forClass(java.time.Instant.class);
        verify(jwt, times(2)).addJWTToken(eq("listener"), anyString(), expiry.capture());
        assertTrue(expiry.getValue().isAfter(java.time.Instant.now().plusSeconds(10790)));
    }

}
