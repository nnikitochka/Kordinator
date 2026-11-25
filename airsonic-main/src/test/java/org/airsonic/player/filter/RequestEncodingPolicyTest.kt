package org.airsonic.player.filter

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestEncodingPolicyTest {
    @Test
    fun usesUtf8AsTheSharedDefaultRequestEncoding() {
        assertEquals("UTF-8", RequestEncodingPolicy.DEFAULT_ENCODING)
    }
}
