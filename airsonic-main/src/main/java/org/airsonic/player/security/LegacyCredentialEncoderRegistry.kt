package org.airsonic.player.security

import org.airsonic.player.service.SettingsService

/** Adapts the shared legacy codecs to the framework-neutral security service API. */
class LegacyCredentialEncoderRegistry(settingsService: SettingsService) : CredentialEncoderRegistry {
    private val encoders = LegacyPasswordEncoders.CODECS

    init {
        LegacyPasswordEncoders.configureEncryption(settingsService)
    }

    override fun encode(encoderName: String, credential: CharSequence): String =
        encoders[encoderName]?.encode(credential)
            ?: throw IllegalArgumentException("Unknown credential encoder: $encoderName")

    override fun matches(encoderName: String, credential: CharSequence, encodedCredential: String): Boolean =
        encoders[encoderName]?.matches(credential, encodedCredential)
            ?: throw IllegalArgumentException("Unknown credential encoder: $encoderName")

    override fun upgradeEncoding(encoderName: String, encodedCredential: String): Boolean =
        encoders[encoderName]?.upgradeEncoding(encodedCredential)
            ?: throw IllegalArgumentException("Unknown credential encoder: $encoderName")

    override fun decode(encoderName: String, encodedCredential: String): String {
        val decoder = encoders[encoderName] as? PasswordDecoder
            ?: throw IllegalArgumentException("Credential encoder is not reversible: $encoderName")
        return decoder.decode(encodedCredential)
    }

    override fun isDecodable(encoderName: String): Boolean = encoderName in LegacyPasswordEncoders.DECODABLE_ENCODERS
    override fun isOpenText(encoderName: String): Boolean = encoderName in LegacyPasswordEncoders.OPENTEXT_ENCODERS
    override fun decodableEncoderNames(): Set<String> = LegacyPasswordEncoders.DECODABLE_ENCODERS
    override fun openTextEncoderNames(): Set<String> = LegacyPasswordEncoders.OPENTEXT_ENCODERS
}
