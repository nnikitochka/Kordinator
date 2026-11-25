package su.nnedition.kord.security

import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.HexFormat
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class AesGcmPasswordEncoder @JvmOverloads constructor(
    password: String = CONSTANT_PASSWORD,
    salt: String = CONSTANT_SALT
) : su.nnedition.kord.security.crypto.PasswordEncoder, PasswordDecoder {
    private val secretKey = deriveSecretKey(password, salt)
    private val secureRandom = SecureRandom()

    @Throws(Exception::class)
    override fun decode(encoded: String): String {
        val decoded = HexFormat.of().parseHex(encoded)
        require(decoded.size >= IV_LENGTH + TAG_LENGTH_BYTES) { "Encrypted value is too short" }
        val iv = decoded.copyOfRange(0, IV_LENGTH)
        val cipherText = decoded.copyOfRange(IV_LENGTH, decoded.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    override fun encode(rawPassword: CharSequence): String {
        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val encrypted = cipher.doFinal(rawPassword.toString().toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(iv + encrypted)
    }

    override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean {
        try {
            return StringUtils.equals(rawPassword, decode(encodedPassword))
        } catch (e: Exception) {
            LOG.warn("Exception while trying to match passwords", e)
            return false
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(AesGcmPasswordEncoder::class.java)

        // The following used as placeholder before the real instance with actual
        // password is instantiated
        private const val CONSTANT_PASSWORD = "kordinatorencryptorpassword"
        private const val CONSTANT_SALT = "44C82DD88E1FA9672628C3637B7F6976"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA1"
        private const val KEY_DERIVATION_ITERATIONS = 1024
        private const val KEY_LENGTH_BITS = 256
        private const val IV_LENGTH = 16
        private const val TAG_LENGTH_BITS = 128
        private const val TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8

        private fun deriveSecretKey(password: String, salt: String): SecretKey {
            val passwordSpec = PBEKeySpec(
                password.toCharArray(),
                HexFormat.of().parseHex(salt),
                KEY_DERIVATION_ITERATIONS,
                KEY_LENGTH_BITS,
            )
            return try {
                val key = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
                    .generateSecret(passwordSpec).encoded
                SecretKeySpec(key, "AES")
            } finally {
                passwordSpec.clearPassword()
            }
        }
    }
}
