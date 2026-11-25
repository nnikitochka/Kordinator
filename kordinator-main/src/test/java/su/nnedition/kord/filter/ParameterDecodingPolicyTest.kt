package su.nnedition.kord.filter

import su.nnedition.kord.util.StringUtil
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParameterDecodingPolicyTest {
    private val policy = ParameterDecodingPolicy()

    @Test
    fun recognizesAndRemovesOnlyLegacySuffix() {
        assertTrue(policy.isEncodedParameter("artistUtf8Hex"))
        assertEquals("artist", policy.decodedParameterName("artistUtf8Hex"))
        assertFalse(policy.isEncodedParameter("artist"))
        assertEquals("artist", policy.decodedParameterName("artist"))
    }

    @Test
    fun decodesAllValuesInOrderWithoutChangingInputArray() {
        val first = StringUtil.utf8HexEncode("Björk")!!
        val second = StringUtil.utf8HexEncode("Тест")!!
        val input = arrayOf(first, second)

        assertArrayEquals(arrayOf("Björk", "Тест"), requireNotNull(policy.decodeValues(input) {}))
        assertArrayEquals(arrayOf(first, second), input)
    }

    @Test
    fun preservesNullAndMalformedValuesWhileReportingFailures() {
        assertEquals(null, policy.decodeValues(null) {})
        val valid = StringUtil.utf8HexEncode("Björk")!!
        val malformed = mutableListOf<String>()

        assertArrayEquals(
            arrayOf("Björk", "not-hex"),
            requireNotNull(policy.decodeValues(arrayOf(valid, "not-hex"), malformed::add)),
        )
        assertEquals(listOf("not-hex"), malformed)
    }
}
