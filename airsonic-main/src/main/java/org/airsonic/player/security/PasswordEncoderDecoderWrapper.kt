package org.airsonic.player.security

class PasswordEncoderDecoderWrapper(
    val encoder: org.airsonic.player.security.crypto.PasswordEncoder,
    val decoder: PasswordDecoder
) : org.airsonic.player.security.crypto.PasswordEncoder, PasswordDecoder {
    override fun encode(rawPassword: CharSequence): String {
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
