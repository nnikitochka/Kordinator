package org.airsonic.player.security

import java.security.Principal
import java.time.Instant

/** Servlet principal carrying only the verified JWT facts needed by Ktor routes. */
data class ExternalJwtPrincipal(
    private val username: String,
    val signedPath: String?,
    val expiresAt: Instant?,
    val roles: Set<String>,
) : Principal {
    override fun getName(): String = username

    fun hasRole(role: String): Boolean = "ROLE_$role" in roles
}
