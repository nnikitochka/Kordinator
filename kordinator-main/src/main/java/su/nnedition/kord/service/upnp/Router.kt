package su.nnedition.kord.service.upnp

/** Controls port mappings on a UPnP/NAT-PMP router. */
interface Router {
    /** Adds a NAT entry; a zero lease duration requests an infinite lease. */
    fun addPortMapping(externalPort: Int, internalPort: Int, leaseDuration: Int)

    /** Deletes a NAT entry. */
    fun deletePortMapping(externalPort: Int, internalPort: Int)
}
