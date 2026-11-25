package su.nnedition.kord.security

import su.nnedition.kord.domain.UserCredential
import su.nnedition.kord.service.SecurityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.Instant

class SubsonicCredentialAuthenticationServiceTest {
    @Test
    fun verifiesCredentialAndPerformsAutomaticUpgradeOutsideSpringProvider() {
        val security = Mockito.mock(SecurityService::class.java)
        val encoders = Mockito.mock(CredentialEncoderRegistry::class.java)
        val credential = UserCredential("alice", "alice", "old", "noop", UserCredential.App.KORDINATOR)
        val identity = UserIdentity("alice", listOf(credential), emptySet())
        Mockito.`when`(security.findUserIdentity("alice", true)).thenReturn(identity)
        Mockito.`when`(encoders.matches("noop", "password", "old")).thenReturn(true)
        Mockito.`when`(encoders.upgradeEncoding("noop", "old")).thenReturn(true)
        Mockito.`when`(
            security.updateCredentials(any<UserCredential>() ?: credential, any<UserCredential>() ?: credential,
                anyString(), anyBoolean()),
        ).thenReturn(true)

        val result = SubsonicCredentialAuthenticationService(security, encoders)
            .authenticate("alice", "password", SubsonicAuthenticator.Mode.PASSWORD)

        assertTrue(result.isAuthenticated)
        assertSame(identity, result.identity)
        val upgraded = ArgumentCaptor.forClass(UserCredential::class.java)
        Mockito.verify(security).updateCredentials(
            eq(credential) ?: credential,
            upgraded.capture() ?: credential,
            eq("null | Automatically upgraded by system") ?: "null | Automatically upgraded by system",
            eq(true) ?: true,
        )
        assertEquals("password", upgraded.value.credential)
    }

    @Test
    fun distinguishesExpiredCredentialsFromInvalidCredentials() {
        val security = Mockito.mock(SecurityService::class.java)
        val encoders = Mockito.mock(CredentialEncoderRegistry::class.java)
        val expired = UserCredential("alice", "alice", "old", "noop", UserCredential.App.KORDINATOR,
            "fixture", Instant.now().minusSeconds(30))
        Mockito.`when`(security.findUserIdentity("alice", true))
            .thenReturn(UserIdentity("alice", listOf(expired), emptySet()))
        Mockito.`when`(encoders.matches("noop", "password", "old")).thenReturn(true)

        val result = SubsonicCredentialAuthenticationService(security, encoders)
            .authenticate("alice", "password", SubsonicAuthenticator.Mode.PASSWORD)

        assertEquals(SubsonicCredentialAuthenticationService.Failure.CREDENTIALS_EXPIRED, result.failure)
    }

    @Test
    fun treatsStoredCredentialsWithRemovedEncoderAsInvalidInsteadOfServerError() {
        val security = Mockito.mock(SecurityService::class.java)
        val encoders = Mockito.mock(CredentialEncoderRegistry::class.java)
        Mockito.`when`(security.findUserIdentity("alice", true)).thenReturn(
            UserIdentity("alice", listOf(UserCredential("alice", "alice", "old", "removed", UserCredential.App.KORDINATOR)), emptySet()),
        )
        Mockito.`when`(encoders.matches("removed", "password", "old"))
            .thenThrow(IllegalArgumentException("Unknown credential encoder"))

        val result = SubsonicCredentialAuthenticationService(security, encoders)
            .authenticate("alice", "password", SubsonicAuthenticator.Mode.PASSWORD)

        assertEquals(SubsonicCredentialAuthenticationService.Failure.INVALID_CREDENTIALS, result.failure)
    }
}
