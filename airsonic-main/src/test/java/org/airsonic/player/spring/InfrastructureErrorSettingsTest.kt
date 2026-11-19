package org.airsonic.player.spring

import org.airsonic.player.runtime.RuntimeConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InfrastructureErrorSettingsTest {
    @Test
    fun `maps error configuration without Spring property binding`() {
        val settings = InfrastructureErrorSettings.from(RuntimeConfiguration(mapOf(
            "server.error.path" to "/failure",
            "server.error.include-exception" to "true",
            "server.error.include-message" to "on_param",
            "server.error.include-stacktrace" to "ALWAYS",
            "server.error.include-path" to "never",
        )))

        assertEquals("/failure", settings.path)
        assertEquals(true, settings.includeException)
        assertEquals(ErrorAttributeMode.ON_PARAM, settings.includeMessage)
        assertEquals(ErrorAttributeMode.ALWAYS, settings.includeStacktrace)
        assertEquals(ErrorAttributeMode.NEVER, settings.includePath)
    }

    @Test
    fun `uses Spring Boot compatible defaults when error settings are absent`() {
        val settings = InfrastructureErrorSettings.from(RuntimeConfiguration(emptyMap()))

        assertEquals("/error", settings.path)
        assertEquals(false, settings.includeException)
        assertEquals(ErrorAttributeMode.NEVER, settings.includeMessage)
        assertEquals(ErrorAttributeMode.NEVER, settings.includeStacktrace)
        assertEquals(ErrorAttributeMode.ALWAYS, settings.includePath)
    }
}
