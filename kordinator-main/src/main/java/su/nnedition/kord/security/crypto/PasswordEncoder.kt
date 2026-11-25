package su.nnedition.kord.security.crypto

interface PasswordEncoder {
    fun encode(rawPassword: CharSequence): String

    fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean

    fun upgradeEncoding(encodedPassword: String): Boolean = false
}
