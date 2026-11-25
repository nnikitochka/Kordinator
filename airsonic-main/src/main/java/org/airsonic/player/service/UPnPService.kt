package org.airsonic.player.service

import org.airsonic.player.service.upnp.CustomContentDirectory
import org.airsonic.player.service.upnp.MSMediaReceiverRegistrarService
import org.airsonic.player.util.FileUtil
import org.fourthline.cling.DefaultUpnpServiceConfiguration
import org.fourthline.cling.UpnpService as ClingUpnpService
import org.fourthline.cling.UpnpServiceImpl
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder
import org.fourthline.cling.model.DefaultServiceManager
import org.fourthline.cling.model.meta.DeviceDetails
import org.fourthline.cling.model.meta.DeviceIdentity
import org.fourthline.cling.model.meta.Icon
import org.fourthline.cling.model.meta.LocalDevice
import org.fourthline.cling.model.meta.LocalService
import org.fourthline.cling.model.meta.ManufacturerDetails
import org.fourthline.cling.model.meta.ModelDetails
import org.fourthline.cling.model.types.DLNADoc
import org.fourthline.cling.model.types.UDADeviceType
import org.fourthline.cling.model.types.UDN
import org.fourthline.cling.support.connectionmanager.ConnectionManagerService
import org.fourthline.cling.support.model.ProtocolInfos
import org.fourthline.cling.support.model.dlna.DLNAProfiles
import org.fourthline.cling.support.model.dlna.DLNAProtocolInfo
import org.fourthline.cling.transport.impl.apache.StreamClientConfigurationImpl
import org.fourthline.cling.transport.impl.apache.StreamClientImpl
import org.fourthline.cling.transport.impl.apache.StreamServerConfigurationImpl
import org.fourthline.cling.transport.impl.apache.StreamServerImpl
import org.fourthline.cling.transport.spi.NetworkAddressFactory
import org.fourthline.cling.transport.spi.StreamClient
import org.fourthline.cling.transport.spi.StreamServer
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class UPnPService(
    settingsService: SettingsService,
    dispatchingContentDirectory: CustomContentDirectory,
) : AutoCloseable {
    private var settings = settingsService
    private var contentDirectory = dispatchingContentDirectory
    private var service: ClingUpnpService? = null
    private val running = AtomicReference(false)

    fun init() {
        if (settings.isDlnaEnabled) {
            ensureServiceStarted()
            setMediaServerEnabled(true)
        }
    }

    override fun close() {
        ensureServiceStopped()
    }

    fun ensureServiceStarted() {
        running.getAndUpdate { wasRunning ->
            if (!wasRunning) startService()
            true
        }
    }

    fun ensureServiceStopped() {
        running.getAndUpdate { wasRunning ->
            if (wasRunning) {
                service?.let { activeService ->
                    LOG.info("Disabling UPnP/DLNA media server")
                    activeService.registry.removeAllLocalDevices()
                    System.err.println("Shutting down UPnP service...")
                    activeService.shutdown()
                    System.err.println("Shutting down UPnP service - Done!")
                }
            }
            false
        }
    }

    private fun startService() {
        try {
            LOG.info("Starting UPnP service...")
            createService()
            LOG.info("Successfully started UPnP service on port {}!", settings.getUPnpPort())
        } catch (failure: Throwable) {
            LOG.error("Failed to start UPnP service: {}", failure, failure)
        }
    }

    @Synchronized
    private fun createService() {
        service = UpnpServiceImpl(ApacheUpnpServiceConfiguration(settings.getUPnpPort()))
        service?.controlPoint?.search()
    }

    fun setMediaServerEnabled(enabled: Boolean) {
        if (enabled) {
            ensureServiceStarted()
            try {
                service!!.registry.addDevice(createMediaServerDevice())
                LOG.info("Enabling UPnP/DLNA media server")
            } catch (failure: Exception) {
                LOG.error("Failed to start UPnP/DLNA media server: {}", failure, failure)
            }
        } else {
            ensureServiceStopped()
        }
    }

    @Throws(Exception::class)
    private fun createMediaServerDevice(): LocalDevice {
        val serverName = settings.dlnaServerName
        val serverId = settings.dlnaServerId ?: UUID.randomUUID().toString().also(settings::setDlnaServerId)
        val identity = DeviceIdentity(UDN.valueOf(serverId))
        val type = UDADeviceType("MediaServer", 1)
        val details = DeviceDetails(
            serverName,
            ManufacturerDetails(serverName),
            ModelDetails(serverName),
            arrayOf(DLNADoc("DMS", DLNADoc.Version.V1_5)),
            null,
        )

        val iconStream: InputStream? = javaClass.getResourceAsStream("logo-512.png")
        val icon = Icon("image/png", 512, 512, 32, "logo-512", iconStream)
        FileUtil.closeQuietly(iconStream)

        val contentDirectoryService = bindService(CustomContentDirectory::class.java)
        contentDirectoryService.setManager(object : DefaultServiceManager<CustomContentDirectory>(contentDirectoryService) {
            override fun createServiceInstance(): CustomContentDirectory = contentDirectory
        })

        val protocols = ProtocolInfos()
        DLNAProfiles.values().filter { it != DLNAProfiles.NONE }.forEach { profile ->
            try {
                protocols.add(DLNAProtocolInfo(profile))
            } catch (_: Exception) {
                // Ignore profiles unsupported by this Cling version.
            }
        }

        val connectionManagerService = bindService(ConnectionManagerService::class.java)
        connectionManagerService.setManager(object : DefaultServiceManager<ConnectionManagerService>(connectionManagerService) {
            override fun createServiceInstance(): ConnectionManagerService = ConnectionManagerService(protocols, null)
        })

        val receiverService = bindService(MSMediaReceiverRegistrarService::class.java)
        receiverService.setManager(object : DefaultServiceManager<MSMediaReceiverRegistrarService>(receiverService) {
            override fun createServiceInstance(): MSMediaReceiverRegistrarService = MSMediaReceiverRegistrarService()
        })

        return LocalDevice(
            identity,
            type,
            details,
            arrayOf(icon),
            arrayOf<LocalService<*>>(contentDirectoryService, connectionManagerService, receiverService),
        )
    }

    fun getUpnpService(): ClingUpnpService? = service

    fun setSettingsService(settingsService: SettingsService) {
        this.settings = settingsService
    }

    fun setCustomContentDirectory(customContentDirectory: CustomContentDirectory) {
        contentDirectory = customContentDirectory
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> bindService(type: Class<T>): LocalService<T> =
        AnnotationLocalServiceBinder().read(type) as LocalService<T>

    class ApacheUpnpServiceConfiguration(streamListenPort: Int) : DefaultUpnpServiceConfiguration(streamListenPort) {
        override fun createStreamClient(): StreamClient<*> =
            StreamClientImpl(StreamClientConfigurationImpl(syncProtocolExecutorService))

        override fun createStreamServer(networkAddressFactory: NetworkAddressFactory): StreamServer<*> =
            StreamServerImpl(StreamServerConfigurationImpl(networkAddressFactory.streamListenPort))
    }

    private companion object {
        val LOG = LoggerFactory.getLogger(UPnPService::class.java)
    }
}
