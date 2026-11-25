package org.airsonic.player.service.upnp

import com.hoodcomputing.natpmp.MapRequestMessage
import com.hoodcomputing.natpmp.NatPmpDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class NATPMPRouterTest {
    @Test
    fun mapsPortsAndUsesOneWeekForAnInfiniteLease() {
        val device = mock(NatPmpDevice::class.java)
        val router = routerFor(device)

        router.addPortMapping(externalPort = 8080, internalPort = 4040, leaseDuration = 0)

        val request = capturedRequest(device)
        assertEquals(4040, request.internalPort)
        assertEquals(8080, request.requestedExternalPort)
        assertEquals(ONE_WEEK_SECONDS.toLong(), request.requestedPortMappingLifetime)
        verify(device).waitUntilQueueEmpty()
    }

    @Test
    fun deletesPortMappingsWithZeroLeaseDuration() {
        val device = mock(NatPmpDevice::class.java)
        val router = routerFor(device)

        router.deletePortMapping(externalPort = 8080, internalPort = 4040)

        val request = capturedRequest(device)
        assertEquals(4040, request.internalPort)
        assertEquals(8080, request.requestedExternalPort)
        assertEquals(0L, request.requestedPortMappingLifetime)
        verify(device).waitUntilQueueEmpty()
    }

    private fun routerFor(device: NatPmpDevice): NATPMPRouter = NATPMPRouter::class.java
        .getDeclaredConstructor(NatPmpDevice::class.java)
        .apply { isAccessible = true }
        .newInstance(device)

    private fun capturedRequest(device: NatPmpDevice): MapRequestMessage {
        val captor = ArgumentCaptor.forClass(MapRequestMessage::class.java)
        verify(device).enqueueMessage(captor.capture())
        return captor.value
    }

    private companion object {
        const val ONE_WEEK_SECONDS = 7 * 24 * 60 * 60
    }
}
