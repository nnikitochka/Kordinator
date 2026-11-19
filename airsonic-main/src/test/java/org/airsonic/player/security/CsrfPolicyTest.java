package org.airsonic.player.security;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CsrfPolicyTest {
    private final CsrfPolicy policy = new CsrfPolicy();

    @Test
    public void exemptsSafeMethodsForAnyPath() {
        for (String method : new String[] {"GET", "HEAD", "TRACE", "OPTIONS"}) {
            assertFalse(method, policy.requiresProtection(method, "/rest/getScanStatus", null, null));
        }
    }

    @Test
    public void preservesPostWhitelistForRestViewPaths() {
        assertFalse(policy.requiresProtection("POST", "/rest/getScanStatus.view", null, null));
        assertFalse(policy.requiresProtection("POST", "/rest/getSong.view", null, "id=1"));
        assertTrue(policy.requiresProtection("POST", "/search", null, null));
        assertTrue(policy.requiresProtection("POST", "/search.view", null, null));
        assertTrue(policy.requiresProtection("POST", "/rest/getScanStatus", null, null));
        assertTrue(policy.requiresProtection("POST", "/search/advanced", null, null));
        assertTrue(policy.requiresProtection("PUT", "/rest/getSong.view", null, null));
    }

    @Test
    public void joinsServletPathPathInfoAndQueryLikeSpringRequestMatcher() {
        assertFalse(policy.requiresProtection("POST", "/rest/getSong", ".view", "id=1"));
        assertTrue(policy.requiresProtection("POST", "/search", null, "query=music"));
    }
}
