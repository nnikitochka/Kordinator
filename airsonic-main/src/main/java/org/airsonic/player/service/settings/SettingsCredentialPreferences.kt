package org.airsonic.player.service.settings

/** Owns signing, encryption, and password encoder preferences. */
internal class SettingsCredentialPreferences(private val values: SettingsValueStore) {
    fun getJwtKey(): String? = values.getString(KEY_JWT_KEY, null)
    fun setJwtKey(key: String?) = values.setProperty(KEY_JWT_KEY, key)
    fun getEncryptionPassword(): String? = values.getString(KEY_ENCRYPTION_PASSWORD, null)
    fun setEncryptionPassword(password: String?) = values.setProperty(KEY_ENCRYPTION_PASSWORD, password)
    fun getEncryptionSalt(): String? = values.getString(KEY_ENCRYPTION_SALT, null)
    fun setEncryptionSalt(salt: String?) = values.setProperty(KEY_ENCRYPTION_SALT, salt)
    fun getDecodablePasswordEncoder(): String = requireNotNull(
        values.getString(KEY_DECODABLE_ENCODER, DEFAULT_DECODABLE_ENCODER),
    )
    fun setDecodablePasswordEncoder(encoder: String?) = values.setProperty(KEY_DECODABLE_ENCODER, encoder)
    fun getNonDecodablePasswordEncoder(): String = requireNotNull(
        values.getString(KEY_NON_DECODABLE_ENCODER, DEFAULT_NON_DECODABLE_ENCODER),
    )
    fun setNonDecodablePasswordEncoder(encoder: String?) = values.setProperty(KEY_NON_DECODABLE_ENCODER, encoder)
    fun getPreferNonDecodablePasswords(): Boolean = values.getBoolean(KEY_PREFER_NON_DECODABLE, true)
    fun setPreferNonDecodablePasswords(preference: Boolean) = values.setProperty(KEY_PREFER_NON_DECODABLE, preference)

    private companion object {
        const val KEY_JWT_KEY = "JWTKey"
        const val KEY_ENCRYPTION_PASSWORD = "EncryptionKeyPassword"
        const val KEY_ENCRYPTION_SALT = "EncryptionKeySalt"
        const val KEY_DECODABLE_ENCODER = "DecodablePasswordEncoder"
        const val KEY_NON_DECODABLE_ENCODER = "NonDecodablePasswordEncoder"
        const val KEY_PREFER_NON_DECODABLE = "PreferNonDecodablePasswords"
        const val DEFAULT_DECODABLE_ENCODER = "encrypted-AES-GCM"
        const val DEFAULT_NON_DECODABLE_ENCODER = "bcrypt"
    }
}
