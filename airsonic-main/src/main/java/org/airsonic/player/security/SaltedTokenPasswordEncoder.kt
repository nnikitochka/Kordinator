package org.airsonic.player.security

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.util.function.Function

open class SaltedTokenPasswordEncoder(
    private val saltTokenExtractor: Function<String, SaltToken>,
    private val encodingFunction: Function<String, String>,
    private val decoder: PasswordDecoder
) : org.airsonic.player.security.crypto.PasswordEncoder {
    constructor(decoder: PasswordDecoder) : this(SaltToken::fromString, DigestUtils::md5Hex, decoder)

    open override fun encode(rawPassword: CharSequence): String = encodingFunction.apply(rawPassword.toString())

    open override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean = try {
        val saltToken = saltTokenExtractor.apply(rawPassword.toString())
        val storedPassword = decoder.decode(encodedPassword)
        StringUtils.equals(saltToken.token, encode(storedPassword + saltToken.salt))
    } catch (e: Exception) {
        LOG.warn("Exception while trying to match passwords", e)
        false
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SaltedTokenPasswordEncoder::class.java)
    }
}
