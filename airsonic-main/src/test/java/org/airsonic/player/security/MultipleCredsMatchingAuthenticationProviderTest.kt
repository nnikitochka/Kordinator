package org.airsonic.player.security

import org.airsonic.player.domain.User
import org.airsonic.player.domain.UserCredential
import org.airsonic.player.service.SecurityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

class MultipleCredsMatchingAuthenticationProviderTest {
    @Test
    fun authenticatesWithStoredCredentialsAndReturnsCurrentRoles() {
        val security = mockSecurity()
        Mockito.`when`(security.findUserIdentity("alice", true)).thenReturn(
            UserIdentity(
                "alice",
                listOf(UserCredential("alice", "alice", "secret", "noop", UserCredential.App.AIRSONIC)),
                setOf(User.Role.ADMIN),
            ),
        )

        val result = provider(security)
            .authenticate(UsernamePasswordAuthenticationToken("alice", "secret"))!!

        assertTrue(result.isAuthenticated)
        assertEquals("alice", result.name)
        assertTrue(result.authorities.any { it.authority == "ROLE_ADMIN" })
        assertTrue(result.credentials == null)
    }

    @Test
    fun rejectsWrongCredentialAndUnsupportedAuthenticationTypes() {
        val security = mockSecurity()
        Mockito.`when`(security.findUserIdentity("alice", true)).thenReturn(
            UserIdentity(
                "alice",
                listOf(UserCredential("alice", "alice", "secret", "noop", UserCredential.App.AIRSONIC)),
                emptySet(),
            ),
        )
        val provider = provider(security)

        assertThrows(BadCredentialsException::class.java) {
            provider.authenticate(UsernamePasswordAuthenticationToken("alice", "wrong"))
        }
        assertFalse(provider.supports(String::class.java))
        assertNull(provider.authenticate(Mockito.mock(org.springframework.security.core.Authentication::class.java)))
    }

    private fun mockSecurity(): SecurityService = Mockito.mock(SecurityService::class.java)

    private fun provider(security: SecurityService) = MultipleCredsMatchingAuthenticationProvider(
        SubsonicCredentialAuthenticationService(security, SpringCredentialEncoderRegistry()),
    )
}
