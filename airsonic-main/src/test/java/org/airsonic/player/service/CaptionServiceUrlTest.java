package org.airsonic.player.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CaptionServiceUrlTest {

    @Test
    public void internalCaptionUrlPreservesRawCaptionIdAndQueryOrder() {
        CaptionService service = captionService(mock(JWTSecurityService.class));

        assertEquals("/airsonic/rest/captions?id=42&captionId=subs%2Fone+two.srt",
                service.getUrl("/airsonic/", null, null, 42, "subs%2Fone+two.srt"));
    }

    @Test
    public void externalCaptionUrlSignsTheSameBaseQueryAndUsesRequestedExpiry() {
        SettingsService settings = mock(SettingsService.class);
        when(settings.getJWTKey()).thenReturn("test-jwt-key");
        CaptionService service = captionService(new JWTSecurityService(settings));
        Instant expires = Instant.parse("2030-01-02T03:04:05Z");

        String url = service.getUrl("/airsonic/", "listener", expires, 42, "subs%2Fone+two.srt");

        assertTrue(url.startsWith("/airsonic/ext/rest/captions?id=42&captionId=subs%2Fone+two.srt&jwt="));
        String token = url.substring(url.indexOf("&jwt=") + "&jwt=".length());
        DecodedJWT decoded = JWTSecurityService.decode(token);
        assertEquals("listener", decoded.getSubject());
        assertEquals("ext/rest/captions?id=42&captionId=subs%252Fone+two.srt", decoded.getClaim(JWTSecurityService.CLAIM_PATH).asString());
        assertEquals(expires, decoded.getExpiresAt().toInstant());
    }

    private CaptionService captionService(JWTSecurityService jwtSecurityService) {
        return new CaptionService(mock(MediaFileService.class), mock(MediaFolderService.class), mock(SettingsService.class),
                mock(org.airsonic.player.service.metadata.MetaDataParserFactory.class), jwtSecurityService);
    }
}
