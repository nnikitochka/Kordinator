package org.airsonic.player.security

import org.airsonic.player.service.SecurityService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

/** Adapts neutral user identity and credential lookup to Spring Security's authentication boundary. */
class SpringUserDetailsServiceAdapter(private val security: SecurityService) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails = loadUserByUsername(username, true)

    fun loadUserByUsername(username: String, caseSensitive: Boolean): UserDetails {
        val identity = security.findUserIdentity(username, caseSensitive)
            ?: throw UsernameNotFoundException("User \"$username\" was not found.")
        val user = security.getUserByName(username, caseSensitive)
            ?: throw UsernameNotFoundException("User \"$username\" was not found.")
        return SpringUserDetailsAdapter(identity, SecurityService.getGrantedAuthorities(user))
    }
}
