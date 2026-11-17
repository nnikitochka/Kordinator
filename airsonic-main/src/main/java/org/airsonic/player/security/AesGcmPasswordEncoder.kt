package org.airsonic.player.security

import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.encrypt.Encryptors

class AesGcmPasswordEncoder @JvmOverloads constructor(
    password: String = CONSTANT_PASSWORD,
    salt: String = CONSTANT_SALT
) : org.airsonic.player.security.crypto.PasswordEncoder, PasswordDecoder {
    private val encryptor = Encryptors.delux(password, salt)

    @Throws(Exception::class)
    override fun decode(encoded: String): String {
        return encryptor.decrypt(encoded)
    }

    override fun encode(rawPassword: CharSequence): String {
        return encryptor.encrypt(rawPassword.toString())
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
        private const val CONSTANT_PASSWORD = "airsonicencryptorpassword"
        private const val CONSTANT_SALT = "44C82DD88E1FA9672628C3637B7F6976"
    }
}
