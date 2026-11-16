package org.airsonic.player.service.upnp;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.airsonic.player.domain.CoverArtScheme;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.SettingsService;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AlbumUpnpProcessorUrlTest {

    @Test
    public void albumArtUrlKeepsRelativePathParametersAndSignsThoseParameters() {
        DispatchingContentDirectory dispatcher = mock(DispatchingContentDirectory.class);
        when(dispatcher.getBaseUrl()).thenReturn("http://media.example.test:4040/airsonic/");
        SettingsService settings = mock(SettingsService.class);
        when(settings.getJWTKey()).thenReturn("test-jwt-key");
        JWTSecurityService jwtSecurityService = new JWTSecurityService(settings);
        when(dispatcher.getJwtSecurityService()).thenReturn(jwtSecurityService);
        AlbumUpnpProcessor processor = new AlbumUpnpProcessor();
        processor.setDispatchingContentDirectory(dispatcher);

        URI url = processor.getAlbumArtURI(37);

        assertEquals("http", url.getScheme());
        assertEquals("media.example.test", url.getHost());
        assertEquals(4040, url.getPort());
        assertEquals("/airsonic/ext/coverArt.view", url.getPath());
        String[] parameters = url.getRawQuery().split("&");
        assertEquals("id=37", parameters[0]);
        assertEquals("size=" + CoverArtScheme.LARGE.getSize(), parameters[1]);
        String token = parameters[2].substring("jwt=".length());
        DecodedJWT decoded = JWTSecurityService.decode(token);
        assertEquals(User.USERNAME_ANONYMOUS, decoded.getSubject());
        assertEquals("ext/coverArt.view?id=37&size=" + CoverArtScheme.LARGE.getSize(),
                decoded.getClaim(JWTSecurityService.CLAIM_PATH).asString());
    }
}
