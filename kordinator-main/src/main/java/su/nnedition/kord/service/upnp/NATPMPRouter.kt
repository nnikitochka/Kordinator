package su.nnedition.kord.service.upnp

import com.hoodcomputing.natpmp.MapRequestMessage
import com.hoodcomputing.natpmp.NatPmpDevice

/** NAT-PMP implementation of [Router]. */
class NATPMPRouter private constructor(
    private val device: NatPmpDevice,
) : Router {
    override fun addPortMapping(externalPort: Int, internalPort: Int, leaseDuration: Int) {
        val effectiveLeaseDuration = if (leaseDuration == 0) DEFAULT_LEASE_DURATION else leaseDuration
        device.enqueueMessage(MapRequestMessage(true, internalPort, externalPort, effectiveLeaseDuration.toLong(), null))
        device.waitUntilQueueEmpty()
    }

    override fun deletePortMapping(externalPort: Int, internalPort: Int) {
        device.enqueueMessage(MapRequestMessage(true, internalPort, externalPort, 0, null))
        device.waitUntilQueueEmpty()
    }

    companion object {
        private const val DEFAULT_LEASE_DURATION = 7 * 24 * 60 * 60

        @JvmStatic
        fun findRouter(): NATPMPRouter? = try {
            NATPMPRouter(NatPmpDevice(false))
        } catch (_: Exception) {
            null
        }
    }
}
