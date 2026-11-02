package org.airsonic.player.security

interface PasswordDecoder {
    @Throws(Exception::class)
    fun decode(encoded: String): String
}
