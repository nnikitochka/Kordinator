package org.airsonic.player.service.upnp;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.TranscodingService;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CustomContentDirectoryUrlTest {

    @Test
    public void audioResourceUrlKeepsQueryOrderAndSignsStreamParameters() {
        DispatchingContentDirectory directory = directory();
        MediaFile song = mock(MediaFile.class);
        when(song.getId()).thenReturn(37);
        when(song.isVideo()).thenReturn(false);
        when(song.getPath()).thenReturn("track.mp3");

        URI url = URI.create(directory.createResourceForSong(song).getValue());

        assertEquals("http", url.getScheme());
        assertEquals("media.example.test", url.getHost());
        assertEquals("/airsonic/ext/stream", url.getPath());
        String[] parameters = url.getRawQuery().split("&");
        assertEquals("id=37", parameters[0]);
        assertEquals("player=9", parameters[1]);
        DecodedJWT decoded = JWTSecurityService.decode(parameters[2].substring("jwt=".length()));
        assertEquals(User.USERNAME_ANONYMOUS, decoded.getSubject());
        assertEquals("ext/stream?id=37&player=9", decoded.getClaim(JWTSecurityService.CLAIM_PATH).asString());
    }

    @Test
    public void videoResourceUrlIncludesRawFormatInSignedQuery() {
        DispatchingContentDirectory directory = directory();
        MediaFile song = mock(MediaFile.class);
        when(song.getId()).thenReturn(38);
        when(song.isVideo()).thenReturn(true);
        when(song.getPath()).thenReturn("clip.mp4");

        URI url = URI.create(directory.createResourceForSong(song).getValue());

        assertEquals("/airsonic/ext/stream", url.getPath());
        String[] parameters = url.getRawQuery().split("&");
        assertEquals("id=38", parameters[0]);
        assertEquals("player=9", parameters[1]);
        assertEquals("format=raw", parameters[2]);
        DecodedJWT decoded = JWTSecurityService.decode(parameters[3].substring("jwt=".length()));
        assertEquals("ext/stream?id=38&player=9&format=raw", decoded.getClaim(JWTSecurityService.CLAIM_PATH).asString());
    }

    private DispatchingContentDirectory directory() {
        Player player = mock(Player.class);
        when(player.getId()).thenReturn(9);
        PlayerService playerService = mock(PlayerService.class);
        when(playerService.getGuestPlayer(null)).thenReturn(player);
        TranscodingService transcodingService = mock(TranscodingService.class);
        when(transcodingService.getSuffix(eq(player), any(MediaFile.class), isNull())).thenReturn("mp3");
        SettingsService settings = mock(SettingsService.class);
        when(settings.getDlnaBaseLANURL()).thenReturn("http://media.example.test/airsonic/");
        when(settings.getJWTKey()).thenReturn("test-jwt-key");
        DispatchingContentDirectory directory = new DispatchingContentDirectory(
                null, null, null, null, null, null, null, null, null, null, null, null);
        directory.setPlayerService(playerService);
        directory.setTranscodingService(transcodingService);
        directory.setSettingsService(settings);
        directory.setJwtSecurityService(new JWTSecurityService(settings));
        return directory;
    }
}
