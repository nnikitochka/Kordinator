package org.airsonic.player.service.upnp;

import com.hoodcomputing.natpmp.MapRequestMessage;
import com.hoodcomputing.natpmp.NatPmpDevice;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public class NATPMPRouter implements Router {

    private final NatPmpDevice device;

    private NATPMPRouter(NatPmpDevice device) {
        this.device = device;
    }

    public static NATPMPRouter findRouter() {
        try {
            return new NATPMPRouter(new NatPmpDevice(false));
        } catch (Exception x) {
            return null;
        }
    }

    public void addPortMapping(int externalPort, int internalPort, int leaseDuration) {

        // Use one week if lease duration is "forever".
        if (leaseDuration == 0) {
            leaseDuration = 7 * 24 * 3600;
        }

        MapRequestMessage map = new MapRequestMessage(true, internalPort, externalPort, leaseDuration, null);
        device.enqueueMessage(map);
        device.waitUntilQueueEmpty();
    }

    public void deletePortMapping(int externalPort, int internalPort) {
        MapRequestMessage map = new MapRequestMessage(true, internalPort, externalPort, 0, null);
        device.enqueueMessage(map);
        device.waitUntilQueueEmpty();
    }
}
