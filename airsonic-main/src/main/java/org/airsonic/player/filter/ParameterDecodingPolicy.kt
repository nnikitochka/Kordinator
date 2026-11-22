package org.airsonic.player.filter

import org.airsonic.player.util.StringUtil
import java.util.function.Consumer

/** Framework-neutral rules for decoding legacy UTF-8-hex request parameters. */
class ParameterDecodingPolicy {
    fun isEncodedParameter(name: String): Boolean = name.endsWith(PARAM_SUFFIX)

    fun decodedParameterName(name: String): String =
        if (isEncodedParameter(name)) name.replace(PARAM_SUFFIX, "") else name

    fun decodeValues(values: Array<String>?, malformedValueHandler: Consumer<String>): Array<String>? {
        if (values == null) return null
        return Array(values.size) { index ->
            val value = values[index]
            try {
                StringUtil.utf8HexDecode(value)
            } catch (_: Exception) {
                malformedValueHandler.accept(value)
                value
            }
        }
    }

    companion object {
        const val PARAM_SUFFIX = "Utf8Hex"
    }
}
