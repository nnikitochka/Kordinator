package org.airsonic.player.runtime

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class StandaloneConfigurationTest {
    @TempDir
    lateinit var home: Path

    @Test
    fun commandLineOverridesJvmEnvironmentAndHomeProperties() {
        Files.writeString(home.resolve("airsonic.properties"), "airsonic.http.port=8081\n")
        val systemProperties = Properties().apply { setProperty("airsonic.http.port", "8082") }
        val environment = mapOf("AIRSONIC_HOME" to home.toString(), "AIRSONIC_HTTP_PORT" to "8083")

        val configuration = StandaloneConfiguration.load(
            arrayOf("--airsonic.http.port=8084"),
            systemProperties,
            environment,
        )

        assertEquals(home, configuration.airsonicHome)
        assertEquals(8084, configuration.getIntProperty("airsonic.http.port", 8080))
    }

    @Test
    fun homePropertiesAreUsedWhenHigherPrioritySourcesAreAbsent() {
        Files.writeString(home.resolve("airsonic.properties"), "airsonic.http.port=8081\n")

        val configuration = StandaloneConfiguration.load(
            emptyArray(),
            Properties(),
            mapOf("AIRSONIC_HOME" to home.toString()),
        )

        assertEquals(8081, configuration.getIntProperty("airsonic.http.port", 8080))
    }
}
