package org.airsonic.player.security

import org.airsonic.player.service.SecurityService
import org.airsonic.player.domain.User
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.core.userdetails.User as SpringUser

/** Adapts neutral user identity and credential lookup to Spring Security's authentication boundary. */
class SpringUserDetailsServiceAdapter(private val security: SecurityService) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails = loadUserByUsername(username, true)

    fun loadUserByUsername(username: String, caseSensitive: Boolean): UserDetails {
        val identity = security.findUserIdentity(username, caseSensitive)
            ?: throw UsernameNotFoundException("User \"$username\" was not found.")
        val authorities = buildList {
            add(SimpleGrantedAuthority("IS_AUTHENTICATED_ANONYMOUSLY"))
            add(SimpleGrantedAuthority("ROLE_USER"))
            identity.roles.forEach { add(SimpleGrantedAuthority("ROLE_${it.name}")) }
        }
        val passwordDigest = DigestUtils.md5Hex(identity.credentials.joinToString("") {
            "${it.encoder}/${it.credential}/${it.expiration}"
        })
        return SpringUser.withUsername(identity.username)
            .password(passwordDigest)
            .authorities(authorities)
            .build()
    }

    companion object {
        @JvmStatic
        fun authorities(user: User): List<GrantedAuthority> = buildList {
            add(SimpleGrantedAuthority("IS_AUTHENTICATED_ANONYMOUSLY"))
            add(SimpleGrantedAuthority("ROLE_USER"))
            user.roles.forEach { add(SimpleGrantedAuthority("ROLE_${it.name}")) }
        }
    }
}
