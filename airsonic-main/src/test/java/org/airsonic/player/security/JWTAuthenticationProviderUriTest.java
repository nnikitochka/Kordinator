package org.airsonic.player.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.airsonic.player.service.JWTSecurityService;
import org.junit.Test;

import java.time.Instant;
import java.util.Date;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JWTAuthenticationProviderUriTest {

    @Test
    public void comparesPathAndQueryUsingSpringUriRules() throws Exception {
        assertTrue(roughlyEqual("/rest/item?x=1", "/rest/item?x=1"));
        assertFalse(roughlyEqual("/rest/item", "/rest/other"));
        assertTrue(roughlyEqual("/rest/item?size=320x240", "/rest/item?size=640x480"));
        assertTrue(roughlyEqual("/rest/item?id=1", "/rest/item?id=1&jwt=token"));
        assertTrue(roughlyEqual("/rest/item?id=1", "/rest/item?id=1&other=value"));
        assertFalse(roughlyEqual("/rest/item?id=1&mode=full", "/rest/item?id=2"));
    }

    @Test
    public void preservesDuplicateQueryValuesAndTheirOrder() throws Exception {
        assertTrue(roughlyEqual("/rest/item?tag=a&tag=b", "/rest/item?tag=a&tag=b"));
        assertTrue(roughlyEqual("/rest/item?tag=a&tag=b", "/rest/item?tag=b&tag=a"));
        assertTrue(roughlyEqual("/rest/item?tag=a", "/rest/item?tag=a&tag=a"));
    }

    @Test
    public void comparesEncodedQueryValuesWithoutUrlDecodingThem() throws Exception {
        assertTrue(roughlyEqual("/rest/item?q=a%2Fb", "/rest/item?q=a%2Fb"));
        assertTrue(roughlyEqual("/rest/item?q=a%2Fb", "/rest/item?q=a/b"));
        assertTrue(roughlyEqual("/rest/item?q=a+b", "/rest/item?q=a%20b"));
        assertFalse(roughlyEqual("/rest/item?a%62=1&x=1", "/rest/item?ab=1&x=2"));
        assertFalse(roughlyEqual("/rest/a%2Fb", "/rest/a/b"));
    }

    @Test
    public void malformedUrisDoNotCompareEqual() throws Exception {
        assertTrue(roughlyEqual("/rest/item?x=%ZZ", "/rest/item?x=%ZZ"));
        assertFalse(roughlyEqual("/rest/item", "http://[::1"));
    }

    @Test
    public void additionalChecksAreSelectedByPathIgnoringQuery() {
        String key = "provider-test-key";
        String rawToken = JWT.create()
                .withSubject("listener")
                .withClaim(JWTSecurityService.CLAIM_PATH, "/rest/item?id=1")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(60)))
                .sign(Algorithm.HMAC256(key));
        JWTAuthenticationProvider provider = new JWTAuthenticationProvider(key);
        boolean[] checked = {false};
        provider.addAdditionalCheck("/rest/item", decoded -> checked[0] = true);

        provider.authenticate(new JWTAuthenticationToken("listener", rawToken,
                "/rest/item?id=1&jwt=client-value"));

        assertTrue(checked[0]);
    }

    private static boolean roughlyEqual(String expected, String requested) {
        return JwtTokenVerifier.roughlyEqual(expected, requested);
    }
}
