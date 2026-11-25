package su.nnedition.kord

import su.nnedition.kord.util.StringUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KotlinSmokeTest {

    @Test
    fun testKotlinCompilationAndJavaInterop() {
        // Kotlin stdlib verification
        val list = listOf("kordinator", "kotlin")
        assertEquals(2, list.size)

        // Kotlin calling Java code from kordinator-main
        val tokens = StringUtil.split("kordinator advanced kotlin")
        assertEquals(3, tokens.size)
        assertEquals("kotlin", tokens[2])
    }
}
