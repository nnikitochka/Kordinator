package su.nnedition.kord.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KordinatorApiRoutePolicyTest {
    @Test
    fun `only versioned Kordinator API paths remain available`() {
        assertTrue(KordinatorApiRoutePolicy.isSupported("/api/v1"))
        assertTrue(KordinatorApiRoutePolicy.isSupported("/api/v1/auth/login"))
        assertTrue(KordinatorApiRoutePolicy.isSupported("/api/v1/tracks/42/stream"))
        assertTrue(KordinatorApiRoutePolicy.isSupported("/internal/ktor/api/v1/auth/login"))

        assertFalse(KordinatorApiRoutePolicy.isSupported("/api/v1legacy"))
        assertFalse(KordinatorApiRoutePolicy.isSupported("/internal/ktor/rest/getSong"))
        assertFalse(KordinatorApiRoutePolicy.isSupported("/rest/getSong"))
        assertFalse(KordinatorApiRoutePolicy.isSupported("/ext/stream"))
        assertFalse(KordinatorApiRoutePolicy.isSupported("/index.view"))
        assertFalse(KordinatorApiRoutePolicy.isSupported("/"))
    }
}
