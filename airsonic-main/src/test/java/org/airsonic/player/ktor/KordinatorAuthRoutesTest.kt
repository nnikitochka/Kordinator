package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.airsonic.player.security.LoginRateLimiter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Instant

class KordinatorAuthRoutesTest {
    @Test
    fun logsInReturnsCurrentUserAndRevokesBearerSessionOnLogout() = testApplication {
        val sessions = Mockito.mock(KordinatorSessionService::class.java)
        Mockito.`when`(sessions.login("alice", "password")).thenReturn(
            KordinatorSessionToken("server-issued-token", Instant.parse("2026-03-01T00:00:00Z")),
        )
        Mockito.`when`(sessions.authenticate("server-issued-token"))
            .thenReturn(KordinatorUserPrincipal("alice", setOf("STREAM", "PLAYLIST")))
        val mapper = jacksonObjectMapper()
        application {
            routing { KordinatorAuthRoutes(sessions, LoginRateLimiter()).routes(this, mapper) }
        }

        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"alice","password":"password"}""")
        }
        val me = client.get("/api/v1/me") { headers.append("Authorization", "Bearer server-issued-token") }
        val logout = client.post("/api/v1/auth/logout") { headers.append("Authorization", "Bearer server-issued-token") }

        assertEquals(HttpStatusCode.OK, login.status)
        assertEquals("no-store", login.headers["Cache-Control"])
        assertEquals("server-issued-token", mapper.readTree(login.bodyAsText())["accessToken"].asText())
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals("alice", mapper.readTree(me.bodyAsText())["username"].asText())
        assertEquals(HttpStatusCode.NoContent, logout.status)
        Mockito.verify(sessions).revoke("server-issued-token")
    }

    @Test
    fun rejectsInvalidCredentialsAndMissingBearerTokens() = testApplication {
        val sessions = Mockito.mock(KordinatorSessionService::class.java)
        Mockito.`when`(sessions.login("alice", "wrong")).thenReturn(null)
        Mockito.`when`(sessions.authenticate(null)).thenReturn(null)
        application {
            routing { KordinatorAuthRoutes(sessions, LoginRateLimiter()).routes(this, jacksonObjectMapper()) }
        }

        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"alice","password":"wrong"}""")
        }
        val me = client.get("/api/v1/me")
        val logout = client.post("/api/v1/auth/logout")

        assertEquals(HttpStatusCode.Unauthorized, login.status)
        assertEquals(HttpStatusCode.Unauthorized, me.status)
        assertEquals(HttpStatusCode.Unauthorized, logout.status)
        assertEquals("unauthorized", mapperErrorCode(login.bodyAsText()))
    }

    private fun mapperErrorCode(body: String) = jacksonObjectMapper().readTree(body)["code"].asText()
}
