package org.airsonic.player.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class JWTSecurityServiceTest {

    private final String key = "someKey";
    private final JWTSecurityService service = new JWTSecurityService(settingsWithKey(key));
    private final String uriString;
    private final Algorithm algorithm = JWTSecurityService.getAlgorithm(key);
    private final JWTVerifier verifier = JWT.require(algorithm).build();
    private final String expectedClaimString;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "http://localhost:8080/airsonic/stream?id=4", "/airsonic/stream?id=4" },
                { "/airsonic/stream?id=4", "/airsonic/stream?id=4" },
                { "?id=4", "?id=4" },
                { "ext/stream?id=a%2Fb", "ext/stream?id=a%252Fb" },
                { "ext/stream?id=%ZZ", "ext/stream?id=%25ZZ" },
        });
    }

    public JWTSecurityServiceTest(String uriString, String expectedClaimString) {
        this.uriString = uriString;
        this.expectedClaimString = expectedClaimString;
    }

    @Test
    public void addJWTToken() {
        String actualUri = service.addJWTToken("xyz", uriString);
        String jwtToken = queryValue(actualUri, JWTSecurityService.JWT_PARAM_NAME);
        DecodedJWT verify = verifier.verify(jwtToken);
        Claim claim = verify.getClaim(JWTSecurityService.CLAIM_PATH);
        assertEquals(expectedClaimString, claim.asString());
        assertEquals("xyz", verify.getSubject());
    }

    @Test
    public void stringOverloadsPreserveUriAndClaimForFragmentAndQueryOnlyInputs() {
        String withFragment = service.addJWTToken("xyz", "https://example.test/path?q=a%2Fb#section");
        assertTrue(withFragment.endsWith("#section"));
        assertTrue(withFragment.contains("?q=a%2Fb&jwt="));
        assertClaimAndOutput("https://example.test/path?q=a%2Fb#section", "/path?q=a%252Fb",
                "https://example.test/path?q=a%2Fb&jwt=");
        assertClaimAndOutput("?q=%ZZ", "?q=%25ZZ", "?q=%ZZ&jwt=");
        assertClaimAndOutput("/path?", "/path", "/path?jwt=");
        assertClaimAndOutput("/path", "/path", "/path?jwt=");
    }

    @Test
    public void mapOverloadPreservesAdditionalClaimsAndExpiration() {
        Instant expiration = Instant.now().plusSeconds(120);
        String signed = service.addJWTToken("xyz", "relative/path?x=1", expiration, Map.of("scope", "test"));
        String jwtToken = queryValue(signed, "jwt");
        DecodedJWT decoded = verifier.verify(jwtToken);

        assertEquals("relative/path?x=1", decoded.getClaim(JWTSecurityService.CLAIM_PATH).asString());
        assertEquals("test", decoded.getClaim("scope").asString());
        assertEquals("xyz", decoded.getSubject());
        assertEquals(expiration.getEpochSecond(), decoded.getExpiresAt().toInstant().getEpochSecond());
    }

    @Test
    public void mapOnlyOverloadUsesDefaultExpiration() {
        String signed = service.addJWTToken("xyz", "relative/path?x=1", Map.of("scope", "test"));
        DecodedJWT decoded = verifier.verify(queryValue(signed, "jwt"));

        assertEquals("relative/path?x=1", decoded.getClaim(JWTSecurityService.CLAIM_PATH).asString());
        assertEquals("test", decoded.getClaim("scope").asString());
        assertEquals("xyz", decoded.getSubject());
        assertTrue(decoded.getExpiresAt().toInstant().isAfter(Instant.now().plusSeconds(6 * 86400L)));
        assertTrue(decoded.getExpiresAt().toInstant().isBefore(Instant.now().plusSeconds(7 * 86400L + 2)));
    }

    @Test
    public void initializesAndPersistsMissingSigningKey() {
        SettingsService settings = mock(SettingsService.class);
        when(settings.getJWTKey()).thenReturn("");

        JWTSecurityService.initializeSigningKey(settings);

        org.mockito.ArgumentCaptor<String> key = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(settings).setJWTKey(key.capture());
        assertTrue(key.getValue().length() >= 20);
        verify(settings).save();
    }

    @Test
    public void leavesExistingSigningKeyUntouched() {
        SettingsService settings = mock(SettingsService.class);
        when(settings.getJWTKey()).thenReturn("existing-signing-key");

        JWTSecurityService.initializeSigningKey(settings);

        verify(settings, never()).setJWTKey(org.mockito.ArgumentMatchers.anyString());
        verify(settings, never()).save();
    }

    @Test
    public void expirationOnlyOverloadUsesTheSuppliedInstant() {
        Instant expiration = Instant.now().plusSeconds(120);
        String signed = service.addJWTToken("xyz", "relative/path?x=1", expiration);
        DecodedJWT decoded = verifier.verify(queryValue(signed, "jwt"));
        assertEquals("relative/path?x=1", decoded.getClaim(JWTSecurityService.CLAIM_PATH).asString());
        assertEquals(expiration.getEpochSecond(), decoded.getExpiresAt().toInstant().getEpochSecond());
    }

    private void assertClaimAndOutput(String input, String expectedClaim, String expectedPrefix) {
        String signed = service.addJWTToken("xyz", input);
        assertEquals(expectedPrefix, signed.substring(0, expectedPrefix.length()));
        DecodedJWT decoded = verifier.verify(queryValue(signed, "jwt"));
        assertEquals(expectedClaim, decoded.getClaim(JWTSecurityService.CLAIM_PATH).asString());
    }

    private static String queryValue(String uri, String name) {
        int fragmentIndex = uri.indexOf('#');
        String withoutFragment = fragmentIndex < 0 ? uri : uri.substring(0, fragmentIndex);
        int queryIndex = withoutFragment.indexOf('?');
        if (queryIndex < 0) throw new AssertionError("Missing query in " + uri);
        for (String parameter : withoutFragment.substring(queryIndex + 1).split("&")) {
            int equalsIndex = parameter.indexOf('=');
            if (equalsIndex >= 0 && parameter.substring(0, equalsIndex).equals(name)) {
                return parameter.substring(equalsIndex + 1);
            }
        }
        throw new AssertionError("Missing " + name + " in " + uri);
    }

    private SettingsService settingsWithKey(String jwtKey) {
        SettingsService settings = mock(SettingsService.class);
        when(settings.getJWTKey()).thenReturn(jwtKey);
        return settings;
    }

}
