package org.airsonic.player.security

import org.apache.commons.text.StringTokenizer

data class SaltToken(
    val salt: String,
    val token: String
) {
    override fun toString(): String {
        return SALT_TOKEN_DELIMITER + salt + SALT_TOKEN_DELIMITER + token + SALT_TOKEN_DELIMITER
    }

    companion object {
        var SALT_TOKEN_DELIMITER: String = "$#\$SALTTOKEN$#$"

        @JvmStatic
        fun fromString(str: String?): SaltToken {
            val splitString = StringTokenizer(str, SALT_TOKEN_DELIMITER)
                .setIgnoreEmptyTokens(false)
                .getTokenArray()

            require(splitString.size == 4) { "Cannot parse into SaltToken object from this String" }

            return SaltToken(splitString[1]!!, splitString[2])
        }
    }
}
