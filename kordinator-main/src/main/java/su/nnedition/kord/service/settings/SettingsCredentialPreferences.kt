package su.nnedition.kord.service.settings

/** Owns signing, encryption, and password encoder preferences. */
internal class SettingsCredentialPreferences(private val values: SettingsValueStore) : SettingsCredentialAccess {
    override fun getJWTKey(): String? = values.getString(KEY_JWT_KEY, null)
    override fun setJWTKey(key: String?) = values.setProperty(KEY_JWT_KEY, key)
    override fun getEncryptionPassword(): String? = values.getString(KEY_ENCRYPTION_PASSWORD, null)
    override fun setEncryptionPassword(password: String?) = values.setProperty(KEY_ENCRYPTION_PASSWORD, password)
    override fun getEncryptionSalt(): String? = values.getString(KEY_ENCRYPTION_SALT, null)
    override fun setEncryptionSalt(salt: String?) = values.setProperty(KEY_ENCRYPTION_SALT, salt)
    override fun getDecodablePasswordEncoder(): String = requireNotNull(
        values.getString(KEY_DECODABLE_ENCODER, DEFAULT_DECODABLE_ENCODER),
    )
    override fun setDecodablePasswordEncoder(encoder: String?) = values.setProperty(KEY_DECODABLE_ENCODER, encoder)
    override fun getNonDecodablePasswordEncoder(): String = requireNotNull(
        values.getString(KEY_NON_DECODABLE_ENCODER, DEFAULT_NON_DECODABLE_ENCODER),
    )
    override fun setNonDecodablePasswordEncoder(encoder: String?) = values.setProperty(KEY_NON_DECODABLE_ENCODER, encoder)
    override fun getPreferNonDecodablePasswords(): Boolean = values.getBoolean(KEY_PREFER_NON_DECODABLE, true)
    override fun setPreferNonDecodablePasswords(preference: Boolean) = values.setProperty(KEY_PREFER_NON_DECODABLE, preference)

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
