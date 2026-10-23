package org.airsonic.player.service.upnp;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public interface Router {

    /**
     * Adds a NAT entry on the UPNP device.
     *
     * @param externalPort  The external port to open on the UPNP device an map on the internal client.
     * @param internalPort  The internal client port where data should be redirected.
     * @param leaseDuration Seconds the lease duration in seconds, or 0 for an infinite time.
     */
    void addPortMapping(int externalPort, int internalPort, int leaseDuration);

    /**
     * Deletes a NAT entry on the UPNP device.
     *
     * @param externalPort The external port of the NAT entry to delete.
     * @param internalPort The internal port of the NAT entry to delete.
     */
    void deletePortMapping(int externalPort, int internalPort);
}
