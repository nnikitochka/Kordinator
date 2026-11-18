package org.airsonic.player.security

/** Framework-neutral operations over the configured credential encoder set. */
interface CredentialEncoderRegistry {
    fun encode(encoderName: String, credential: CharSequence): String
    fun matches(encoderName: String, credential: CharSequence, encodedCredential: String): Boolean
    fun decode(encoderName: String, encodedCredential: String): String
    fun isDecodable(encoderName: String): Boolean
    fun isOpenText(encoderName: String): Boolean
    fun decodableEncoderNames(): Set<String>
    fun openTextEncoderNames(): Set<String>
}
