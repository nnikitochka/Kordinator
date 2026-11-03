package org.airsonic.player.security

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority

class JWTAuthenticationToken : UsernamePasswordAuthenticationToken {
    val requestedPath: String

    constructor(principal: Any?, credentials: Any, requestedPath: String) : super(principal, credentials) {
        this.requestedPath = requestedPath
    }

    constructor(
        principal: Any,
        credentials: Any,
        requestedPath: String,
        authorities: MutableCollection<out GrantedAuthority>
    ) : super(principal, credentials, authorities) {
        this.requestedPath = requestedPath
    }

    constructor(
        principal: Any,
        credentials: Any,
        requestedPath: String,
        authorities: MutableCollection<out GrantedAuthority>,
        details: Any
    ) : this(principal, credentials, requestedPath, authorities) {
        this.details = details
    }
}
