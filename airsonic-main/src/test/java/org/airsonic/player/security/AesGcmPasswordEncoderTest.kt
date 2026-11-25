package org.airsonic.player.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test

class AesGcmPasswordEncoderTest {
    private val encoder = AesGcmPasswordEncoder(TEST_PASS, TEST_SALT)

    @Test
    fun testEncryptionAndDecryption() {
        val plaintext = "randomstring"
        assertThat(encoder.decode(encoder.encode(plaintext))).isEqualTo(plaintext)
    }

    @Test
    fun testEncryptionAndDecryptionAnotherEncoderSamePassword() {
        val plaintext = "randomstring"
        val anotherEncoder = AesGcmPasswordEncoder(TEST_PASS, TEST_SALT)
        assertThat(encoder.decode(anotherEncoder.encode(plaintext))).isEqualTo(plaintext)
    }

    @Test
    fun testEncryptionAndDecryptionAnotherEncoderDifferentPassword() {
        val plaintext = "randomstring"
        val differentPasswordEncoder = AesGcmPasswordEncoder(TEST_PASS + "a", TEST_SALT)
        assertThatExceptionOfType(Exception::class.java)
            .isThrownBy { encoder.decode(differentPasswordEncoder.encode(plaintext)) }
        val differentSaltEncoder = AesGcmPasswordEncoder(TEST_PASS, TEST_SALT + "55")
        assertThatExceptionOfType(Exception::class.java)
            .isThrownBy { encoder.decode(differentSaltEncoder.encode(plaintext)) }
    }

    @Test
    fun testMultipleEncryptionsYieldsDifferentResults() {
        val plaintext = "randomstring"
        assertThat(encoder.encode(plaintext)).isNotEqualTo(encoder.encode(plaintext))
    }

    @Test
    fun testMatch() {
        val plaintext = "randomstring"
        val encodedFirst = encoder.encode(plaintext)
        val encodedSecond = encoder.encode(plaintext)
        assertThat(encoder.matches(plaintext, encodedFirst)).isTrue()
        assertThat(encoder.matches(plaintext, encodedSecond)).isTrue()
        assertThat(encoder.matches(plaintext + "a", encodedSecond)).isFalse()
    }

    @Test
    fun testNonMatchWithDifferentPassword() {
        val plaintext = "randomstring"
        val differentPasswordEncoder = AesGcmPasswordEncoder(TEST_PASS + "a", TEST_SALT)
        assertThat(differentPasswordEncoder.matches(plaintext, encoder.encode(plaintext))).isFalse()
    }

    private companion object {
        const val TEST_PASS = "testpass"
        const val TEST_SALT = "2D9AAC35B41C0DB14C8170BE555DC0C9"
    }
}
