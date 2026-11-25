package su.nnedition.kord.security

import java.time.Instant

/** Authentication facts needed to construct HLS links, detached from web security types. */
data class HlsLinkContext(val username: String, val jwtAuthenticated: Boolean, val jwtExpiresAt: Instant?)
