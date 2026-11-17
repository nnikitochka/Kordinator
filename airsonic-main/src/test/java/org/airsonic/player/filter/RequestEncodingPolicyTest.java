package org.airsonic.player.filter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RequestEncodingPolicyTest {
    @Test
    public void usesUtf8AsTheSharedDefaultRequestEncoding() {
        assertEquals("UTF-8", RequestEncodingPolicy.DEFAULT_ENCODING);
    }
}
