package org.airsonic.player.service

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class VersionServiceTest {
    @Test
    fun loadsAndCachesLocalBuildMetadata() {
        val service = VersionService()

        val firstVersion = service.getLocalVersion()

        assertNotNull(firstVersion)
        assertSame(firstVersion, service.getLocalVersion())
        assertNotNull(service.getLocalBuildDate())
        assertNotNull(service.getLocalBuildNumber())
    }
}
