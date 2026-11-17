package org.airsonic.player.filter;

import org.airsonic.player.util.StringUtil;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ParameterDecodingPolicyTest {
    private final ParameterDecodingPolicy policy = new ParameterDecodingPolicy();

    @Test
    public void recognizesAndRemovesOnlyLegacySuffix() {
        assertTrue(policy.isEncodedParameter("artistUtf8Hex"));
        assertEquals("artist", policy.decodedParameterName("artistUtf8Hex"));
        assertFalse(policy.isEncodedParameter("artist"));
        assertEquals("artist", policy.decodedParameterName("artist"));
    }

    @Test
    public void decodesAllValuesInOrderWithoutChangingInputArray() {
        String first = StringUtil.utf8HexEncode("Björk");
        String second = StringUtil.utf8HexEncode("Тест");
        String[] input = {first, second};

        assertArrayEquals(new String[] {"Björk", "Тест"}, policy.decodeValues(input, ignored -> { }));
        assertArrayEquals(new String[] {first, second}, input);
    }

    @Test
    public void preservesNullValuesAndMalformedValuesWhileReportingFailures() {
        assertArrayEquals(null, policy.decodeValues(null, ignored -> { }));
        String valid = StringUtil.utf8HexEncode("Björk");
        java.util.List<String> malformed = new java.util.ArrayList<>();
        assertArrayEquals(new String[] {"Björk", "not-hex"},
                policy.decodeValues(new String[] {valid, "not-hex"}, malformed::add));
        assertEquals(java.util.List.of("not-hex"), malformed);
    }
}
