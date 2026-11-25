package su.nnedition.kord.service

import su.nnedition.kord.service.upnp.CustomContentDirectory
import org.fourthline.cling.UpnpService as ClingUpnpService
import org.fourthline.cling.registry.Registry
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.ArgumentMatchers.anyString
import java.util.concurrent.atomic.AtomicReference

class UPnPServiceLifecycleTest {
    @Test
    fun initializationLeavesDlnaStoppedWhenDisabled() {
        val settings = mock(SettingsService::class.java)
        `when`(settings.isDlnaEnabled()).thenReturn(false)
        val service = UPnPService(settings, mock(CustomContentDirectory::class.java))

        service.init()

        assertNull(service.getUpnpService())
    }

    @Test
    fun closeRemovesDevicesAndShutsDownAnActiveServiceOnlyOnce() {
        val service = UPnPService(mock(SettingsService::class.java), mock(CustomContentDirectory::class.java))
        val clingService = mock(ClingUpnpService::class.java)
        val registry = mock(Registry::class.java)
        `when`(clingService.registry).thenReturn(registry)
        UPnPService::class.java.getDeclaredField("service").apply {
            isAccessible = true
            set(service, clingService)
        }
        val running = UPnPService::class.java.getDeclaredField("running").let { field ->
            field.isAccessible = true
            field.get(service) as AtomicReference<Boolean>
        }
        running.set(true)

        service.close()
        service.close()

        verify(registry).removeAllLocalDevices()
        verify(clingService, times(1)).shutdown()
    }

    @Test
    fun mediaServerDeviceKeepsItsIconServicesAndGeneratedIdentity() {
        val settings = mock(SettingsService::class.java)
        val contentDirectory = mock(CustomContentDirectory::class.java)
        `when`(settings.getDlnaServerName()).thenReturn("Kordinator")
        `when`(settings.getDlnaServerId()).thenReturn(null)
        val service = UPnPService(settings, contentDirectory)
        val createDevice = UPnPService::class.java.getDeclaredMethod("createMediaServerDevice").apply {
            isAccessible = true
        }

        val device = createDevice.invoke(service) as org.fourthline.cling.model.meta.LocalDevice

        assertEquals("Kordinator", device.details.friendlyName)
        assertEquals(3, device.services.size)
        assertEquals(512, device.icons.single().width)
        device.services.forEach { service -> assertNotNull(service.manager) }
        verify(settings).setDlnaServerId(anyString())
    }
}
