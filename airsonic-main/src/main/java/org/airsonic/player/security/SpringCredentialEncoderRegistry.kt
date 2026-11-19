package org.airsonic.player.security

import org.airsonic.player.security.GlobalSecurityConfig.DECODABLE_ENCODERS
import org.airsonic.player.security.GlobalSecurityConfig.ENCODERS
import org.airsonic.player.security.GlobalSecurityConfig.OPENTEXT_ENCODERS

/** Bridges the legacy Spring encoder map into the neutral credential registry. */
class SpringCredentialEncoderRegistry : CredentialEncoderRegistry {
    override fun encode(encoderName: String, credential: CharSequence): String =
        ENCODERS[encoderName]?.encode(credential)
            ?: throw IllegalArgumentException("Unknown credential encoder: $encoderName")

    override fun matches(encoderName: String, credential: CharSequence, encodedCredential: String): Boolean =
        ENCODERS[encoderName]?.matches(credential, encodedCredential)
            ?: throw IllegalArgumentException("Unknown credential encoder: $encoderName")

    override fun upgradeEncoding(encoderName: String, encodedCredential: String): Boolean =
        ENCODERS[encoderName]?.upgradeEncoding(encodedCredential)
            ?: throw IllegalArgumentException("Unknown credential encoder: $encoderName")

    override fun decode(encoderName: String, encodedCredential: String): String {
        val decoder = ENCODERS[encoderName] as? PasswordDecoder
            ?: throw IllegalArgumentException("Credential encoder is not reversible: $encoderName")
        return decoder.decode(encodedCredential)
    }

    override fun isDecodable(encoderName: String): Boolean = encoderName in DECODABLE_ENCODERS
    override fun isOpenText(encoderName: String): Boolean = encoderName in OPENTEXT_ENCODERS
    override fun decodableEncoderNames(): Set<String> = DECODABLE_ENCODERS
    override fun openTextEncoderNames(): Set<String> = OPENTEXT_ENCODERS
}
