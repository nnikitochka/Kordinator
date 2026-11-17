package org.airsonic.player.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.airsonic.player.dao.ShareDao;
import org.airsonic.player.domain.Share;
import org.airsonic.player.domain.User;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ShareServiceUrlTest {

    @Test
    public void shareUrlSignsTheRelativeSharePathWithShareExpiry() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getRequestURL()).thenReturn(new StringBuffer("https://media.example.test:8443/airsonic/share/list"));
        when(request.getContextPath()).thenReturn("/airsonic");
        SettingsService settings = mock(SettingsService.class);
        when(settings.getJWTKey()).thenReturn("test-jwt-key");
        ShareService service = new ShareService(mock(ShareDao.class), mock(MediaFileService.class), new JWTSecurityService(settings));
        Share share = new Share();
        share.setName("ShareName1");
        Instant expires = Instant.parse("2030-02-03T04:05:06Z");
        share.setExpires(expires);

        String shareUrl = service.getShareUrl(request, share);

        assertTrue(shareUrl.startsWith("https://media.example.test:8443/airsonic/ext/share/ShareName1?jwt="));
        String token = shareUrl.substring(shareUrl.indexOf("?jwt=") + "?jwt=".length());
        DecodedJWT decoded = JWTSecurityService.decode(token);
        assertEquals(User.USERNAME_ANONYMOUS, decoded.getSubject());
        assertEquals("ext/share/ShareName1", decoded.getClaim(JWTSecurityService.CLAIM_PATH).asString());
        assertEquals(expires, decoded.getExpiresAt().toInstant());
    }
}
