package org.airsonic.player

import org.airsonic.player.util.StringUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KotlinSmokeTest {

    @Test
    fun testKotlinCompilationAndJavaInterop() {
        // Kotlin stdlib verification
        val list = listOf("airsonic", "kotlin")
        assertEquals(2, list.size)

        // Kotlin calling Java code from airsonic-main
        val tokens = StringUtil.split("airsonic advanced kotlin")
        assertEquals(3, tokens.size)
        assertEquals("kotlin", tokens[2])
    }
}
