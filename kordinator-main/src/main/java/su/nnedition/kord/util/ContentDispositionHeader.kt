package su.nnedition.kord.util

import java.nio.charset.StandardCharsets

/** Builds RFC 6266 attachment headers without depending on Spring HTTP utilities. */
object ContentDispositionHeader {
    fun attachment(filename: String): String {
        val fallback = filename
            .map { character ->
                if (character.code in 0x20..0x7e && character != '"' && character != '\\') character else '_'
            }
            .joinToString("")
        val encoded = buildString {
            for (byte in filename.toByteArray(StandardCharsets.UTF_8)) {
                val value = byte.toInt() and 0xff
                if (value in 'a'.code..'z'.code || value in 'A'.code..'Z'.code ||
                    value in '0'.code..'9'.code || value.toChar() in SAFE_ATTRIBUTE_CHARACTERS
                ) {
                    append(value.toChar())
                } else {
                    append('%')
                    append(HEX[value ushr 4])
                    append(HEX[value and 0x0f])
                }
            }
        }
        return "attachment; filename=\"$fallback\"; filename*=UTF-8''$encoded"
    }

    private val SAFE_ATTRIBUTE_CHARACTERS = setOf('!', '#', '$', '&', '+', '-', '.', '^', '_', '`', '|', '~')
    private const val HEX = "0123456789ABCDEF"
}
