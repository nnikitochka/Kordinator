package org.airsonic.player.security

import com.google.common.io.BaseEncoding
import org.apache.commons.lang3.StringUtils
import org.springframework.security.crypto.password.PasswordEncoder
import java.nio.charset.StandardCharsets

class HexPasswordEncoder private constructor() : PasswordEncoder, PasswordDecoder {
    override fun encode(rawPassword: CharSequence): String? {
        return hex.encode(rawPassword.toString().toByteArray(StandardCharsets.UTF_8))
    }

    override fun decode(encoded: String): String {
        return String(hex.decode(encoded), StandardCharsets.UTF_8)
    }

    override fun matches(rawPassword: CharSequence, encodedPassword: String?): Boolean {
        return StringUtils.equals(encode(rawPassword), encodedPassword)
    }

    companion object {
        private val hex: BaseEncoding = BaseEncoding.base16().lowerCase()

        @JvmStatic
        val instance: HexPasswordEncoder = HexPasswordEncoder()
    }
}
