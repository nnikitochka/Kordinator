package org.airsonic.player.ktor

import org.airsonic.player.domain.User
import org.airsonic.player.domain.UserCredential
import org.airsonic.player.security.CredentialEncoderRegistry
import org.airsonic.player.security.UserIdentity
import org.airsonic.player.service.SecurityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class KordinatorSessionServiceTest {
    private val now = Instant.parse("2026-02-03T04:05:06Z")

    @Test
    fun issuesOpaqueTokenAndStoresOnlyItsHash() {
        val security = Mockito.mock(SecurityService::class.java)
        val encoders = Mockito.mock(CredentialEncoderRegistry::class.java)
        val store = Mockito.mock(KordinatorSessionStore::class.java)
        val credential = UserCredential("alice", "alice", "stored-hash", "argon2", UserCredential.App.AIRSONIC)
            .apply { updated = now.minusSeconds(60) }
        val identity = UserIdentity("alice", listOf(credential), setOf(User.Role.STREAM))
        Mockito.`when`(security.findUserIdentity("alice", true)).thenReturn(identity)
        Mockito.`when`(security.getUserByName("alice")).thenReturn(
            User("alice", null, false, 0, 0, 0, setOf(User.Role.STREAM)),
        )
        Mockito.`when`(security.getPreferredPasswordEncoder(true)).thenReturn("argon2")
        Mockito.`when`(encoders.matches("argon2", "correct horse", "stored-hash")).thenReturn(true)
        Mockito.`when`(encoders.isOpenText("argon2")).thenReturn(false)
        val service = service(security, encoders, store)

        val token = service.login("alice", "correct horse")

        assertNotNull(token)
        assertEquals(now.plus(Duration.ofDays(30)), token!!.expiresAt)
        assertEquals(43, token.accessToken.length)
        val saved = Mockito.mockingDetails(store).invocations.single { it.method.name == "create" }
        val storedHash = saved.arguments[1] as String
        assertNotEquals(token.accessToken, storedHash)
        assertEquals(sha256(token.accessToken), storedHash)
        assertEquals("alice", saved.arguments[0])
        assertEquals(now.minusSeconds(60), saved.arguments[2])
    }

    @Test
    fun requestsUpgradeFromOpenTextPasswordToConfiguredOneWayEncoderAtLogin() {
        val security = Mockito.mock(SecurityService::class.java)
        val encoders = Mockito.mock(CredentialEncoderRegistry::class.java)
        val store = Mockito.mock(KordinatorSessionStore::class.java)
        val credential = UserCredential("alice", "alice", "plain-password", "legacynoop", UserCredential.App.AIRSONIC, "old password")
            .apply { updated = now.minusSeconds(60) }
        val identity = UserIdentity("alice", listOf(credential), setOf(User.Role.STREAM))
        Mockito.`when`(security.findUserIdentity("alice", true)).thenReturn(identity)
        Mockito.`when`(security.getUserByName("alice")).thenReturn(
            User("alice", null, false, 0, 0, 0, setOf(User.Role.STREAM)),
        )
        Mockito.`when`(security.getPreferredPasswordEncoder(true)).thenReturn("argon2")
        Mockito.`when`(encoders.matches("legacynoop", "plain-password", "plain-password")).thenReturn(true)
        Mockito.`when`(encoders.isOpenText("legacynoop")).thenReturn(true)
        Mockito.`when`(security.updateCredentials(
            Mockito.eq(credential), Mockito.any(UserCredential::class.java), Mockito.anyString(), Mockito.eq(true),
        )).thenReturn(true)
        Mockito.`when`(security.findUserIdentity("alice", true)).thenReturn(identity)
        val service = service(security, encoders, store)

        val token = service.login("alice", "plain-password")

        assertNotNull(token)
        val updated = Mockito.mockingDetails(security).invocations
            .single { it.method.name == "updateCredentials" }.arguments[1] as UserCredential
        assertEquals("argon2", updated.encoder)
        assertEquals("plain-password", updated.credential)
        val updateCall = Mockito.mockingDetails(security).invocations.single { it.method.name == "updateCredentials" }
        assertEquals(true, updateCall.arguments[3])
    }

    @Test
    fun rejectsWrongPasswordAndDoesNotCreateSession() {
        val security = Mockito.mock(SecurityService::class.java)
        val encoders = Mockito.mock(CredentialEncoderRegistry::class.java)
        val store = Mockito.mock(KordinatorSessionStore::class.java)
        val credential = UserCredential("alice", "alice", "stored-hash", "argon2", UserCredential.App.AIRSONIC)
        Mockito.`when`(security.findUserIdentity("alice", true)).thenReturn(
            UserIdentity("alice", listOf(credential), setOf(User.Role.STREAM)),
        )
        Mockito.`when`(security.getUserByName("alice")).thenReturn(
            User("alice", null, false, 0, 0, 0, setOf(User.Role.STREAM)),
        )
        Mockito.`when`(security.getPreferredPasswordEncoder(true)).thenReturn("argon2")
        Mockito.`when`(encoders.encode("argon2", "kordinator-authentication-dummy-password")).thenReturn("dummy-hash")
        Mockito.`when`(encoders.matches("argon2", "wrong password", "stored-hash")).thenReturn(false)
        Mockito.`when`(encoders.matches("argon2", "wrong password", "dummy-hash")).thenReturn(false)
        Mockito.`when`(encoders.isOpenText("argon2")).thenReturn(false)

        assertNull(service(security, encoders, store).login("alice", "wrong password"))
        Mockito.verifyNoInteractions(store)
    }

    @Test
    fun rejectsExpiredAndDeletedSessionsAndRevokesCredentialVersionChanges() {
        val security = Mockito.mock(SecurityService::class.java)
        val encoders = Mockito.mock(CredentialEncoderRegistry::class.java)
        val store = Mockito.mock(KordinatorSessionStore::class.java)
        val token = "opaque-session-token"
        val hash = sha256(token)
        val credential = UserCredential("alice", "alice", "stored-hash", "argon2", UserCredential.App.AIRSONIC)
            .apply { updated = now }
        val identity = UserIdentity("alice", listOf(credential), setOf(User.Role.STREAM))
        Mockito.`when`(store.findActive(hash, now)).thenReturn(KordinatorSessionRecord("alice", now.minusSeconds(1)))
        Mockito.`when`(security.findUserIdentity("alice", true)).thenReturn(identity)
        Mockito.`when`(security.getUserByName("alice")).thenReturn(
            User("alice", null, false, 0, 0, 0, setOf(User.Role.STREAM)),
        )

        assertNull(service(security, encoders, store).authenticate(token))
        Mockito.verify(store).revoke(hash)
    }

    @Test
    fun rejectsUnknownUsersAndLdapAccounts() {
        val security = Mockito.mock(SecurityService::class.java)
        val encoders = Mockito.mock(CredentialEncoderRegistry::class.java)
        val store = Mockito.mock(KordinatorSessionStore::class.java)
        Mockito.`when`(security.getPreferredPasswordEncoder(true)).thenReturn("argon2")
        Mockito.`when`(encoders.encode("argon2", "kordinator-authentication-dummy-password")).thenReturn("dummy-hash")
        Mockito.`when`(encoders.matches("argon2", "attempt", "dummy-hash")).thenReturn(false)
        Mockito.`when`(security.findUserIdentity("missing", true)).thenReturn(null)

        assertNull(service(security, encoders, store).login("missing", "attempt"))
        Mockito.verifyNoInteractions(store)

        val ldapUser = User("ldap-user", null, true, 0, 0, 0, setOf(User.Role.STREAM))
        Mockito.`when`(security.findUserIdentity("ldap-user", true)).thenReturn(
            UserIdentity("ldap-user", emptyList(), setOf(User.Role.STREAM)),
        )
        Mockito.`when`(security.getUserByName("ldap-user")).thenReturn(ldapUser)
        assertNull(service(security, encoders, store).login("ldap-user", "attempt"))
    }

    private fun service(
        security: SecurityService,
        encoders: CredentialEncoderRegistry,
        store: KordinatorSessionStore,
    ) = KordinatorSessionService(
        security, encoders, store, Clock.fixed(now, ZoneOffset.UTC),
        secureRandom = object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) = bytes.fill(7)
        },
    )

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
