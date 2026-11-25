package su.nnedition.kord.security

interface PasswordDecoder {
    @Throws(Exception::class)
    fun decode(encoded: String): String
}
