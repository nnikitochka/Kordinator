package org.airsonic.player.security

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.domain.User
import org.airsonic.player.service.SecurityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.security.core.Authentication

class SpringCallerContextAdapterTest {
    @Test
    fun adaptsAuthenticatedRequestPrincipal() {
        val security = Mockito.mock(SecurityService::class.java)
        val authentication = Mockito.mock(Authentication::class.java)
        val request = Mockito.mock(HttpServletRequest::class.java)
        val context = CallerContext("alice", setOf(User.Role.ADMIN), setOf(2), true)
        Mockito.`when`(request.userPrincipal).thenReturn(authentication)
        Mockito.`when`(authentication.isAuthenticated).thenReturn(true)
        Mockito.`when`(authentication.name).thenReturn("alice")
        Mockito.`when`(security.createCallerContext("alice", true)).thenReturn(context)

        val actual = SpringCallerContextAdapter(security).fromRequest(request)

        assertSame(context, actual)
        Mockito.verify(security).createCallerContext("alice", true)
    }

    @Test
    fun returnsAnonymousWithoutAuthenticatedServletPrincipal() {
        val security = Mockito.mock(SecurityService::class.java)
        val request = Mockito.mock(HttpServletRequest::class.java)
        Mockito.`when`(request.userPrincipal).thenReturn(null)

        val actual = SpringCallerContextAdapter(security).fromRequest(request)

        assertEquals(User.USERNAME_ANONYMOUS, actual.username)
        assertFalse(actual.isAuthenticated)
        assertTrue(actual.roles.isEmpty())
        Mockito.verifyNoInteractions(security)
    }
}
