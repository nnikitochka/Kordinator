package org.airsonic.player.security

import org.springframework.security.crypto.password.PasswordEncoder

class PasswordEncoderDecoderWrapper(
    val encoder: PasswordEncoder,
    val decoder: PasswordDecoder
) : PasswordEncoder, PasswordDecoder {
    override fun encode(rawPassword: CharSequence?): String? {
        return encoder.encode(rawPassword)
    }

    @Throws(Exception::class)
    override fun decode(encoded: String): String {
        return decoder.decode(encoded)
    }

    override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean {
        return encoder.matches(rawPassword, encodedPassword)
    }
}
