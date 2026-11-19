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
import java.security.Principal

class ServletCallerContextAdapterTest {
    @Test
    fun adaptsAuthenticatedRequestPrincipal() {
        val security = Mockito.mock(SecurityService::class.java)
        val request = Mockito.mock(HttpServletRequest::class.java)
        val context = CallerContext("alice", setOf(User.Role.ADMIN), setOf(2), true)
        Mockito.`when`(request.userPrincipal).thenReturn(Principal { "alice" })
        Mockito.`when`(security.createCallerContext("alice", true)).thenReturn(context)

        val actual = ServletCallerContextAdapter(security).fromRequest(request)

        assertSame(context, actual)
        Mockito.verify(security).createCallerContext("alice", true)
    }

    @Test
    fun returnsAnonymousWithoutServletPrincipal() {
        val security = Mockito.mock(SecurityService::class.java)
        val request = Mockito.mock(HttpServletRequest::class.java)
        Mockito.`when`(request.userPrincipal).thenReturn(null)

        val actual = ServletCallerContextAdapter(security).fromRequest(request)

        assertEquals(User.USERNAME_ANONYMOUS, actual.username)
        assertFalse(actual.isAuthenticated)
        assertTrue(actual.roles.isEmpty())
        Mockito.verifyNoInteractions(security)
    }
}
